package cn.bugstack.ai.domain.agent.model.dynamicteam.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 已接受计划中的临时 Worker 定义。
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DynamicTeamWorkerVO {

    private String workerId;
    private String roleName;
    private String responsibility;
    private String instruction;
    /**
     * 服务端将按此列表为临时 Worker 装配工具；空列表表示无工具。
     */
    private List<String> neededCapabilities;

}
