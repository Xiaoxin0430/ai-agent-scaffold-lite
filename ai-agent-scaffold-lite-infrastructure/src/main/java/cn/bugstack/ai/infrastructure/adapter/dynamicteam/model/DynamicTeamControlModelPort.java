package cn.bugstack.ai.infrastructure.adapter.dynamicteam.model;

import cn.bugstack.ai.domain.agent.adapter.port.dynamicteam.IDynamicTeamControlModelPort;
import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamControlRoleEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 使用后端固定 ChatModel 执行无工具的控制角色调用。
 */
@Slf4j
@Service
public class DynamicTeamControlModelPort implements IDynamicTeamControlModelPort {

    @Resource(name = "controlChatModel")
    private ChatModel chatModel;

    @Override
    public String generate(
            DynamicTeamControlRoleEnum controlRole,
            String systemInstruction,
            String dynamicInput) {

        if (controlRole == null) {
            throw new IllegalArgumentException("控制角色不能为空。");
        }
        if (systemInstruction == null || systemInstruction.isBlank()) {
            throw new IllegalArgumentException("固定控制提示词不能为空。");
        }
        if (dynamicInput == null || dynamicInput.isBlank()) {
            throw new IllegalArgumentException("控制模型动态输入不能为空。");
        }

        log.info("动态团队控制模型调用开始 role={}, systemLength={}, inputLength={}",
                controlRole.name(), systemInstruction.length(), dynamicInput.length());

        try {
            Prompt prompt = new Prompt(
                    new SystemMessage(systemInstruction),
                    new UserMessage(dynamicInput)
            );

            ChatResponse response = chatModel.call(prompt);
            String output = extractOutput(response);

            if (output == null || output.isBlank()) {
                log.warn("动态团队控制模型返回空文本 role={}, responsePresent={}, resultPresent={}, outputPresent={}",
                        controlRole.name(), response != null,
                        response != null && response.getResult() != null,
                        response != null && response.getResult() != null
                                && response.getResult().getOutput() != null);
                throw new IllegalStateException("控制模型返回内容为空，role=" + controlRole.name());
            }

            log.info("动态团队控制模型调用完成 role={}, outputLength={}",
                    controlRole.name(), output.length());

            return output;
        } catch (RuntimeException exception) {
            log.warn("动态团队控制模型调用失败 role={}, errorType={}",
                    controlRole.name(), exception.getClass().getSimpleName());
            throw new IllegalStateException(
                    "动态团队控制模型调用失败，role=" + controlRole.name(), exception);
        }
    }

    private String extractOutput(ChatResponse response) {
        if (response == null
                || response.getResult() == null
                || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

}
