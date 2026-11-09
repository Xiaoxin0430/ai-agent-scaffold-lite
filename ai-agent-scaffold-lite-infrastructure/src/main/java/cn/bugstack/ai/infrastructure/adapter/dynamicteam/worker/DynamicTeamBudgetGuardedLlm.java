package cn.bugstack.ai.infrastructure.adapter.dynamicteam.worker;

import cn.bugstack.ai.domain.agent.model.dynamicteam.run.DynamicTeamModelCallBudgetEntity;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import io.reactivex.rxjava3.core.Flowable;

/** 在每次真实 BaseLlm 调用前预占运行级模型额度。 */
public class DynamicTeamBudgetGuardedLlm extends BaseLlm {

    private final BaseLlm delegate;
    private final DynamicTeamModelCallBudgetEntity modelCallBudget;

    public DynamicTeamBudgetGuardedLlm(
            BaseLlm delegate,
            DynamicTeamModelCallBudgetEntity modelCallBudget) {
        super(delegate.model());
        if (modelCallBudget == null) {
            throw new IllegalArgumentException("模型调用预算不能为空。");
        }
        this.delegate = delegate;
        this.modelCallBudget = modelCallBudget;
    }

    @Override
    public Flowable<LlmResponse> generateContent(
            LlmRequest request,
            boolean stream) {
        try {
            // 在委托给真实模型前预占额度，多轮 generateContent 会逐次计数。
            modelCallBudget.reserveModelCall();
            return delegate.generateContent(request, stream);
        } catch (RuntimeException exception) {
            return Flowable.error(exception);
        }
    }

    @Override
    public BaseLlmConnection connect(LlmRequest request) {
        return delegate.connect(request);
    }
}
