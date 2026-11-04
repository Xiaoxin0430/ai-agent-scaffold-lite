package cn.bugstack.ai.domain.agent.model.valobj.canvas;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;

/**
 * 画布经过后端校验与转换后的领域结果。
 * 校验失败时 config 为 null；校验成功时 config 为可预览或发布的运行配置。
 */
public record WorkflowCanvasConversionResultVO(
        CanvasValidationResultVO validation,
        AiAgentConfigTableVO config) {

    public boolean valid() {
        return validation.valid();
    }
}
