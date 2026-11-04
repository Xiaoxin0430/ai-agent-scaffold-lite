package cn.bugstack.ai.domain.agent.service.canvas;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.types.exception.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/** Creates a runtime copy without changing the configuration saved in PostgreSQL. */
@Service
public class WorkflowRuntimeConfigFactory {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private Environment environment;

    public AiAgentConfigTableVO create(AiAgentConfigTableVO source) {
        if (source == null || source.getModule() == null || source.getModule().getAiApi() == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "工作流模型配置缺失。");
        }
        String placeholder = source.getModule().getAiApi().getApiKey();
        if (placeholder == null || !placeholder.matches("\\$\\{[A-Za-z_][A-Za-z0-9_]*:?}")) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "模型 API Key 必须使用环境变量占位符。");
        }
        String apiKey = environment.resolvePlaceholders(placeholder);
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("${")) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "模型 API Key 环境变量未配置。");
        }

        AiAgentConfigTableVO runtime = objectMapper.convertValue(source, AiAgentConfigTableVO.class);
        runtime.getModule().getAiApi().setApiKey(apiKey);
        return runtime;
    }
}
