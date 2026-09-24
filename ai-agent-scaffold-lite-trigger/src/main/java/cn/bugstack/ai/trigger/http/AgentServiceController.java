package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.IAgentService;
import cn.bugstack.ai.api.dto.*;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.model.valobj.ChatStreamResultVO;
import cn.bugstack.ai.domain.agent.service.IChatService;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.types.exception.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.events.Event;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/1/20 08:23
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin(origins = "*")
public class AgentServiceController implements IAgentService {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private IChatService chatService;

    @RequestMapping(value = "query_ai_agent_config_list", method = RequestMethod.GET)
    @Override
    public Response<List<AiAgentConfigResponseDTO>> queryAiAgentConfigList() {
        try {
            log.info("查询智能体配置列表");

            List<AiAgentConfigTableVO.Agent> agentConfigs = chatService.queryAiAgentConfigList();

            List<AiAgentConfigResponseDTO> responseDTOS = agentConfigs.stream().map(agentConfig -> {
                AiAgentConfigResponseDTO responseDTO = new AiAgentConfigResponseDTO();
                responseDTO.setAgentId(agentConfig.getAgentId());
                responseDTO.setAgentName(agentConfig.getAgentName());
                responseDTO.setAgentDesc(agentConfig.getAgentDesc());
                return responseDTO;
            }).collect(Collectors.toList());

            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOS)
                    .build();

        } catch (AppException e) {
            log.error("查询智能体配置列表异常", e);
            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("查询智能体配置列表失败", e);
            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "create_session", method = RequestMethod.POST)
    @Override
    public Response<CreateSessionResponseDTO> createSession(@RequestBody CreateSessionRequestDTO requestDTO) {
        try {
            log.info("创建会话 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId());
            String sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());

            CreateSessionResponseDTO responseDTO = new CreateSessionResponseDTO();
            responseDTO.setSessionId(sessionId);

            return Response.<CreateSessionResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("查询智能体配置列表异常", e);
            return Response.<CreateSessionResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("创建会话失败 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId(), e);
            return Response.<CreateSessionResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "create_session", method = RequestMethod.GET)
    public Response<CreateSessionResponseDTO> createSession(@RequestParam("agentId") String agentId, @RequestParam("userId") String userId) {
        CreateSessionRequestDTO requestDTO = new CreateSessionRequestDTO();
        requestDTO.setAgentId(agentId);
        requestDTO.setUserId(userId);
        return createSession(requestDTO);
    }

    @RequestMapping(value = "chat", method = RequestMethod.POST)
    @Override
    public Response<ChatResponseDTO> chat(@RequestBody ChatRequestDTO requestDTO) {
        try {
            log.info("智能体对话 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId());
            String sessionId = requestDTO.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());
            }

            List<String> messages = chatService.handleMessage(requestDTO.getAgentId(), requestDTO.getUserId(), sessionId, requestDTO.getMessage());

            ChatResponseDTO responseDTO = new ChatResponseDTO();
            responseDTO.setContent(String.join("\n", messages));

            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("智能体对话异常", e);
            return Response.<ChatResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("智能体对话败 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId(), e);
            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "chat_stream", method = RequestMethod.POST, produces = "application/x-ndjson")
    @Override
    public ResponseBodyEmitter chatStream(@RequestBody ChatRequestDTO requestDTO) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(3 * 60 * 1000L);
        AtomicBoolean finished = new AtomicBoolean();
        try {
            log.info("流式对话 agentId:{} userId:{} sessionId:{} message:{}", requestDTO.getAgentId(), requestDTO.getUserId(), requestDTO.getSessionId(), requestDTO.getMessage());
            ChatStreamResultVO chatResult = chatService.prepareMessageStream(
                    requestDTO.getAgentId(), requestDTO.getUserId(), requestDTO.getSessionId(), requestDTO.getMessage());
            if (!chatResult.sources().isEmpty()) {
                List<Map<String, Object>> sources = new java.util.ArrayList<>();
                for (int i = 0; i < chatResult.sources().size(); i++) {
                    var source = chatResult.sources().get(i);
                    if (source.getChunk() == null) continue;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("ref", "S" + (i + 1));
                    item.put("documentId", source.getChunk().getDocumentId());
                    item.put("fileName", source.getFileName());
                    item.put("chunkIndex", source.getChunk().getChunkIndex());
                    item.put("similarity", source.getSimilarity());
                    sources.add(item);
                }
                sendFrame(emitter, Map.of("type", "sources", "sources", sources));
            }
            chatResult.events()
                    .subscribeOn(Schedulers.io())
                    .takeUntil(event -> event.turnComplete().orElse(false)
                            && event.branch().orElse("").isEmpty()
                            && event.content().map(content -> content.parts().orElse(List.of()).stream()
                                    .map(Part::text).flatMap(java.util.Optional::stream)
                                    .anyMatch(text -> !text.isBlank())).orElse(false))
                    .subscribe(
                            event -> {
                                try {
                                    Map<String, Object> frame = new LinkedHashMap<>();
                                    frame.put("type", "message");
                                    frame.put("author", event.author());
                                    frame.put("branch", event.branch().orElse(""));
                                    frame.put("partial", event.partial().orElse(false));
                                    frame.put("text", event.content().map(content -> content.parts().orElse(List.of()).stream()
                                            .map(Part::text).flatMap(java.util.Optional::stream)
                                            .collect(Collectors.joining())).orElse(""));
                                    sendFrame(emitter, frame);
                                } catch (Exception e) {
                                    log.error("流式对话发送失败", e);
                                    if (finished.compareAndSet(false, true)) emitter.completeWithError(e);
                                }
                            },
                            error -> {
                                log.error("流式对话失败", error);
                                if (finished.compareAndSet(false, true)) {
                                    try {
                                        sendFrame(emitter, Map.of("type", "error", "message", "对话生成失败，请重试"));
                                        emitter.complete();
                                    } catch (Exception sendError) {
                                        emitter.completeWithError(sendError);
                                    }
                                }
                            },
                            () -> {
                                if (finished.compareAndSet(false, true)) {
                                    try {
                                        sendFrame(emitter, Map.of("type", "done"));
                                        emitter.complete();
                                    } catch (Exception e) {
                                        emitter.completeWithError(e);
                                    }
                                }
                            }
                    );
        } catch (Exception e) {
            log.error("流式对话失败", e);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private void sendFrame(ResponseBodyEmitter emitter, Map<String, Object> frame) throws Exception {
        emitter.send(objectMapper.writeValueAsString(frame) + "\n", MediaType.TEXT_PLAIN);
    }

}
