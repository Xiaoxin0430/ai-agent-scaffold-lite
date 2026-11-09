package cn.bugstack.ai.api;

import cn.bugstack.ai.api.dto.DynamicTeamCapabilitiesDTO;
import cn.bugstack.ai.api.dto.DynamicTeamRunAcceptedDTO;
import cn.bugstack.ai.api.dto.DynamicTeamRunSnapshotDTO;
import cn.bugstack.ai.api.dto.DynamicTeamRunStartRequestDTO;
import cn.bugstack.ai.api.response.Response;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IDynamicTeamRunApi {

    Response<DynamicTeamCapabilitiesDTO> capabilities();

    Response<DynamicTeamRunAcceptedDTO> start(DynamicTeamRunStartRequestDTO request);

    Response<DynamicTeamRunSnapshotDTO> query(String runId);

    SseEmitter stream(String runId, String lastEventId, Long afterSequence);

    Response<DynamicTeamRunSnapshotDTO> cancel(String runId);

    Response<DynamicTeamRunSnapshotDTO> result(String runId);
}
