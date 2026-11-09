package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.IDynamicTeamRunApi;
import cn.bugstack.ai.api.dto.DynamicTeamCapabilitiesDTO;
import cn.bugstack.ai.api.dto.DynamicTeamRunAcceptedDTO;
import cn.bugstack.ai.api.dto.DynamicTeamRunEventDTO;
import cn.bugstack.ai.api.dto.DynamicTeamRunSnapshotDTO;
import cn.bugstack.ai.api.dto.DynamicTeamRunStartRequestDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunStartCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunEventVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRunSnapshotVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;
import cn.bugstack.ai.domain.agent.service.dynamicteam.run.IDynamicTeamRunService;
import cn.bugstack.ai.domain.agent.service.dynamicteam.run.DynamicTeamRunEventSubscription;
import cn.bugstack.ai.types.enums.ResponseCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/v1/dynamic-team")
@CrossOrigin(origins = "*")
public class DynamicTeamRunController implements IDynamicTeamRunApi {

    @Resource
    private IDynamicTeamRunService dynamicTeamRunService;
    @Resource
    private ObjectMapper objectMapper;

    private final ExecutorService streamExecutor = new ThreadPoolExecutor(
            2, 16, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100),
            new ThreadPoolExecutor.AbortPolicy());

    @Override
    @GetMapping("/capabilities")
    public Response<DynamicTeamCapabilitiesDTO> capabilities() {
        DynamicTeamRuntimePolicyVO policy = dynamicTeamRunService.capabilities();
        return success(DynamicTeamCapabilitiesDTO.builder()
                .enabled(policy.isEnabled())
                .maxWorkers(policy.getMaxWorkers())
                .maxTasks(policy.getMaxTasks())
                .maxConcurrentWorkers(policy.getMaxConcurrentWorkers())
                .stepTimeoutSeconds(policy.getStepTimeoutSeconds())
                .runTimeoutSeconds(policy.getRunTimeoutSeconds())
                .maxModelCalls(policy.getMaxModelCalls())
                .maxUserInputCharacters(policy.getMaxUserInputCharacters())
                .build());
    }

    @Override
    @PostMapping("/runs")
    public Response<DynamicTeamRunAcceptedDTO> start(
            @RequestBody DynamicTeamRunStartRequestDTO request) {
        try {
            var context = dynamicTeamRunService.start(
                    DynamicTeamRunStartCommandEntity.builder()
                    .userGoal(request == null ? null : request.getUserGoal())
                    .acceptanceRequirements(request == null
                            || request.getAcceptanceRequirements() == null
                            ? List.of() : request.getAcceptanceRequirements())
                    .build());
            return success(DynamicTeamRunAcceptedDTO.builder()
                    .runId(context.getRunId())
                    .status(context.getStatus().name())
                    .build());
        } catch (IllegalArgumentException exception) {
            return failure(ResponseCode.ILLEGAL_PARAMETER, exception.getMessage());
        } catch (IllegalStateException exception) {
            return failure(ResponseCode.UN_ERROR, exception.getMessage());
        }
    }

    @Override
    @GetMapping("/runs/{runId}")
    public Response<DynamicTeamRunSnapshotDTO> query(@PathVariable String runId) {
        return snapshotResponse(runId);
    }

    @Override
    @GetMapping(value = "/runs/{runId}/events/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String runId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @RequestParam(value = "afterSequence", required = false) Long afterSequence) {
        long cursor = parseCursor(lastEventId, afterSequence);
        SseEmitter emitter = new SseEmitter(0L);
        DynamicTeamRunEventSubscription subscription = dynamicTeamRunService.subscribe(runId, cursor);
        emitter.onCompletion(subscription::close);
        emitter.onTimeout(subscription::close);
        emitter.onError(error -> subscription.close());
        try {
            streamExecutor.execute(() -> pumpEvents(emitter, subscription));
        } catch (RuntimeException exception) {
            subscription.close();
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    @Override
    @PostMapping("/runs/{runId}/cancel")
    public Response<DynamicTeamRunSnapshotDTO> cancel(@PathVariable String runId) {
        try {
            return success(toSnapshot(dynamicTeamRunService.cancel(runId)));
        } catch (IllegalArgumentException exception) {
            return failure(ResponseCode.ILLEGAL_PARAMETER, exception.getMessage());
        }
    }

    @Override
    @GetMapping("/runs/{runId}/result")
    public Response<DynamicTeamRunSnapshotDTO> result(@PathVariable String runId) {
        return snapshotResponse(runId);
    }

    @PreDestroy
    public void shutdownStreamExecutor() {
        streamExecutor.shutdownNow();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Response<Void> handleIllegalArgument(IllegalArgumentException exception) {
        return failure(ResponseCode.ILLEGAL_PARAMETER, exception.getMessage());
    }

    private void pumpEvents(
            SseEmitter emitter, DynamicTeamRunEventSubscription subscription) {
        try (subscription) {
            while (true) {
                DynamicTeamRunEventVO event = subscription.poll(15, TimeUnit.SECONDS);
                if (event == null) {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                    continue;
                }
                emitter.send(SseEmitter.event()
                        .id(Long.toString(event.getSequence()))
                        .name("dynamic-team-event")
                        .data(toEvent(event)));
                if (event.getRunStatus().isTerminal()) {
                    emitter.complete();
                    return;
                }
            }
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (RuntimeException exception) {
            log.warn("动态团队 SSE 发送失败", exception);
            emitter.completeWithError(exception);
        }
    }

    private Response<DynamicTeamRunSnapshotDTO> snapshotResponse(String runId) {
        try {
            return success(toSnapshot(dynamicTeamRunService.query(runId)));
        } catch (IllegalArgumentException exception) {
            return failure(ResponseCode.ILLEGAL_PARAMETER, exception.getMessage());
        }
    }

    private DynamicTeamRunSnapshotDTO toSnapshot(DynamicTeamRunSnapshotVO snapshot) {
        Map<String, Object> plan = snapshot.getCurrentPlan() == null ? null
                : objectMapper.convertValue(snapshot.getCurrentPlan(), new TypeReference<>() { });
        List<Map<String, Object>> results = objectMapper.convertValue(
                snapshot.getCurrentTaskResults(), new TypeReference<>() { });
        long lastSequence = snapshot.getEvents().isEmpty() ? 0L
                : snapshot.getEvents().get(snapshot.getEvents().size() - 1).getSequence();
        return DynamicTeamRunSnapshotDTO.builder()
                .runId(snapshot.getRunId())
                .userGoal(snapshot.getUserGoal())
                .acceptanceRequirements(snapshot.getAcceptanceRequirements())
                .status(snapshot.getStatus().name())
                .terminationReason(snapshot.getTerminationReason() == null ? null
                        : snapshot.getTerminationReason().name())
                .currentPlan(plan)
                .currentTaskResults(results)
                .usedModelCalls(snapshot.getUsedModelCalls())
                .remainingModelCalls(snapshot.getRemainingModelCalls())
                .startedAt(snapshot.getStartedAt())
                .resourcesReleased(snapshot.isResourcesReleased())
                .lastEventSequence(lastSequence)
                .finalAnswer(snapshot.getFinalAnswer())
                .build();
    }

    private DynamicTeamRunEventDTO toEvent(DynamicTeamRunEventVO event) {
        return DynamicTeamRunEventDTO.builder()
                .sequence(event.getSequence())
                .runId(event.getRunId())
                .planVersion(event.getPlanVersion())
                .eventType(event.getEventType().name())
                .runStatus(event.getRunStatus().name())
                .workerId(event.getWorkerId())
                .taskId(event.getTaskId())
                .reasonCode(event.getReasonCode())
                .message(event.getMessage())
                .occurredAt(event.getOccurredAt())
                .build();
    }

    private long parseCursor(String lastEventId, Long afterSequence) {
        if (afterSequence != null) {
            return Math.max(0L, afterSequence);
        }
        try {
            return lastEventId == null ? 0L : Math.max(0L, Long.parseLong(lastEventId));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private <T> Response<T> failure(ResponseCode code, String message) {
        return Response.<T>builder()
                .code(code.getCode())
                .info(message == null ? code.getInfo() : message)
                .build();
    }
}
