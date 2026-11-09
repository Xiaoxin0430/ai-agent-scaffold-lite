package cn.bugstack.ai.test.domain.agent;

import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationCommandEntity;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanningModeEnum;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanDraftVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationIssueVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.plan.DynamicTeamPlanValidationResultVO;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamRuntimePolicyVO;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanFactory;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanValidationService;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanContextValidationRule;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanDependencyValidationRule;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanTaskValidationRule;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanValidator;
import cn.bugstack.ai.domain.agent.service.dynamicteam.planning.validation.DynamicTeamPlanWorkerValidationRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicTeamPlanValidationServiceTest {

    private DynamicTeamPlanValidationService validationService;

    @BeforeEach
    void setUp() {
        DynamicTeamPlanValidator validator = new DynamicTeamPlanValidator(
                new DynamicTeamPlanContextValidationRule(),
                new DynamicTeamPlanWorkerValidationRule(),
                new DynamicTeamPlanTaskValidationRule(),
                new DynamicTeamPlanDependencyValidationRule()
        );
        validationService = new DynamicTeamPlanValidationService(
                validator,
                new DynamicTeamPlanFactory()
        );
    }

    @Test
    void shouldCreateVersionOneForValidInitialPlan() {
        DynamicTeamPlanDraftVO draft = validDraft();

        DynamicTeamPlanValidationResultVO result = validationService.validateAndBuild(
                initialCommand(draft)
        );

        assertTrue(result.isValid());
        assertTrue(result.getIssues().isEmpty());
        assertEquals("run_001", result.getAcceptedPlan().getRunId());
        assertEquals(1, result.getAcceptedPlan().getPlanVersion());
        assertNull(result.getAcceptedPlan().getPreviousPlanVersion());
        assertEquals(2, result.getAcceptedPlan().getWorkers().size());
        assertEquals(2, result.getAcceptedPlan().getTasks().size());

        draft.getWorkers().get(0).setRoleName("已修改的草稿角色");
        assertEquals("需求分析师",
                result.getAcceptedPlan().getWorkers().get(0).getRoleName());
    }

    @Test
    void shouldCollectMultipleValidationIssues() {
        DynamicTeamPlanDraftVO.WorkerDraft worker = new DynamicTeamPlanDraftVO.WorkerDraft(
                "Bad-Worker", "", "", ""
        );
        DynamicTeamPlanDraftVO.TaskDraft task = new DynamicTeamPlanDraftVO.TaskDraft(
                "Bad-Task", "missing_worker", "", "", "", List.of(), null
        );
        DynamicTeamPlanDraftVO draft = new DynamicTeamPlanDraftVO(
                "2.0", "", "", List.of(worker), List.of(task)
        );

        DynamicTeamPlanValidationResultVO result = validationService.validateAndBuild(
                initialCommand(draft)
        );

        assertFalse(result.isValid());
        assertNull(result.getAcceptedPlan());
        assertTrue(result.getIssues().size() >= 8);
        assertTrue(hasIssue(result, "SCHEMA_VERSION_UNSUPPORTED"));
        assertTrue(hasIssue(result, "WORKER_ID_INVALID"));
        assertTrue(hasIssue(result, "TASK_ID_INVALID"));
        assertTrue(hasIssue(result, "TASK_WORKER_NOT_FOUND"));
        assertTrue(hasIssue(result, "ACCEPTANCE_CRITERIA_REQUIRED"));
    }

    @Test
    void shouldRejectTaskDependencyCycle() {
        DynamicTeamPlanDraftVO draft = validDraft();
        draft.getTasks().get(0).setDependsOn(List.of("design_solution"));

        DynamicTeamPlanValidationResultVO result = validationService.validateAndBuild(
                initialCommand(draft)
        );

        assertFalse(result.isValid());
        assertTrue(hasIssue(result, "TASK_DEPENDENCY_CYCLE"));
    }

    private DynamicTeamPlanValidationCommandEntity initialCommand(
            DynamicTeamPlanDraftVO draft) {
        return DynamicTeamPlanValidationCommandEntity.builder()
                .runId("run_001")
                .planningMode(DynamicTeamPlanningModeEnum.INITIAL)
                .planDraft(draft)
                .runtimePolicy(runtimePolicy())
                .build();
    }

    private DynamicTeamRuntimePolicyVO runtimePolicy() {
        return DynamicTeamRuntimePolicyVO.builder()
                .enabled(true)
                .maxWorkers(5)
                .maxTasks(20)
                .maxConcurrentWorkers(3)
                .maxReplans(2)
                .stepTimeoutSeconds(60)
                .runTimeoutSeconds(300)
                .maxModelCalls(25)
                .maxUserInputCharacters(20000)
                .build();
    }

    private DynamicTeamPlanDraftVO validDraft() {
        List<DynamicTeamPlanDraftVO.WorkerDraft> workers = new ArrayList<>();
        workers.add(new DynamicTeamPlanDraftVO.WorkerDraft(
                "requirement_analyst",
                "需求分析师",
                "分析目标、约束和验收条件",
                "只分析需求，不编写最终方案"
        ));
        workers.add(new DynamicTeamPlanDraftVO.WorkerDraft(
                "solution_designer",
                "方案设计师",
                "根据需求分析设计方案",
                "基于上游结果输出技术方案"
        ));

        List<DynamicTeamPlanDraftVO.TaskDraft> tasks = new ArrayList<>();
        tasks.add(new DynamicTeamPlanDraftVO.TaskDraft(
                "analyze_requirement",
                "requirement_analyst",
                "分析需求",
                "提取目标、约束和风险",
                "结构化需求分析",
                List.of("包含目标", "包含约束"),
                new ArrayList<>()
        ));
        tasks.add(new DynamicTeamPlanDraftVO.TaskDraft(
                "design_solution",
                "solution_designer",
                "设计方案",
                "根据需求分析生成方案",
                "可实施技术方案",
                List.of("说明调用链", "说明异常处理"),
                new ArrayList<>(List.of("analyze_requirement"))
        ));

        return new DynamicTeamPlanDraftVO(
                "1.0",
                "先分析需求，再设计方案",
                "INITIAL_PLAN",
                workers,
                tasks
        );
    }

    private boolean hasIssue(
            DynamicTeamPlanValidationResultVO result,
            String expectedCode) {
        for (DynamicTeamPlanValidationIssueVO issue : result.getIssues()) {
            if (expectedCode.equals(issue.getCode())) {
                return true;
            }
        }
        return false;
    }

}
