package cn.bugstack.ai.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class DynamicTeamRunStartRequestDTO {
    private String userGoal;
    private List<String> acceptanceRequirements;
}
