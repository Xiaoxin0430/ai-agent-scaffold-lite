package cn.bugstack.ai.domain.agent.service.chat;

import cn.bugstack.ai.domain.agent.model.entity.ChatCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.bugstack.ai.domain.agent.model.valobj.ChatStreamResultVO;
import cn.bugstack.ai.domain.agent.model.valobj.RagRouteDecision;
import cn.bugstack.ai.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import cn.bugstack.ai.domain.agent.service.IChatService;
import cn.bugstack.ai.domain.agent.service.armory.factory.DefaultArmoryFactory;
import cn.bugstack.ai.domain.agent.service.router.IRagRouter;
import cn.bugstack.ai.domain.rag.model.valobj.SimilarDocumentChunkVO;
import cn.bugstack.ai.domain.rag.service.IDocumentRagService;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.types.exception.AppException;
import com.google.adk.events.Event;
import com.google.adk.agents.RunConfig;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ChatService implements IChatService {

    @Resource
    private DefaultArmoryFactory defaultArmoryFactory;

    @Resource
    private AiAgentAutoConfigProperties aiAgentAutoConfigProperties;

    @Resource
    private IRagRouter ragRouter;

    @Resource
    private IDocumentRagService documentRagService;

    @Resource
    private RagChatContextBuilder ragChatContextBuilder;

    @Value("${rag.chat.min-similarity:0.35}")
    private double ragMinSimilarity;

    private final Map<String, String> userSessions = new ConcurrentHashMap<>();

    @Override
    public List<AiAgentConfigTableVO.Agent> queryAiAgentConfigList() {
        Map<String, AiAgentConfigTableVO> tables = aiAgentAutoConfigProperties.getTables();

        List<AiAgentConfigTableVO.Agent> agentList = new ArrayList<>();
        if (null != tables) {
            for (AiAgentConfigTableVO vo : tables.values()) {
                if (null != vo.getAgent()) {
                    agentList.add(vo.getAgent());
                }
            }
        }

        return agentList;
    }

    @Override
    public String createSession(String agentId, String userId) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);

        if (null == aiAgentRegisterVO) {
            throw new AppException(ResponseCode.E0001.getCode());
        }

        String appName = aiAgentRegisterVO.getAppName();
        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        return userSessions.computeIfAbsent(userId, uid -> {
            Session session = runner.sessionService().createSession(appName, uid)
                    .blockingGet();
            return session.id();
        });
    }

    @Override
    public List<String> handleMessage(String agentId, String userId, String message) {

        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);

        if (null == aiAgentRegisterVO) {
            throw new AppException(ResponseCode.E0001.getCode());
        }

        String sessionId = createSession(agentId, userId);

        return handleMessage(agentId, userId, sessionId, message);
    }

    @Override
    public List<String> handleMessage(String agentId, String userId, String sessionId, String message) {

        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);

        if (null == aiAgentRegisterVO) {
            throw new AppException(ResponseCode.E0001.getCode());
        }

        Flowable<Event> events = prepareMessageStream(agentId, userId, sessionId, message).events();

        List<String> outputs = new ArrayList<>();
        events.filter(event -> event.turnComplete().orElse(false)
                        && event.branch().orElse("").isEmpty()
                        && event.content().map(content -> content.parts().orElse(List.of()).stream()
                                .map(Part::text).flatMap(java.util.Optional::stream)
                                .anyMatch(text -> !text.isBlank())).orElse(false))
                .take(1)
                .blockingForEach(event -> outputs.add(event.stringifyContent()));

        return outputs;
    }

    @Override
    public Flowable<Event> handleMessageStream(String agentId, String userId, String sessionId, String message) {
        return prepareMessageStream(agentId, userId, sessionId, message).events();
    }

    @Override
    public ChatStreamResultVO prepareMessageStream(String agentId, String userId, String sessionId, String message) {
        if (userId == null || userId.isBlank() || sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("userId and sessionId cannot be blank");
        }
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);

        if (null == aiAgentRegisterVO) {
            throw new AppException(ResponseCode.E0001.getCode());
        }

        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        RagRouteDecision decision = ragRouter.decide(message, recentAnswer(runner, aiAgentRegisterVO.getAppName(), userId, sessionId));
        List<SimilarDocumentChunkVO> sources = decision.retrieve()
                ? ragChatContextBuilder.selectSources(documentRagService.searchSimilarChunks(
                        decision.searchQuery(), null, ragMinSimilarity, List.of(), userId))
                : List.of();
        String context = decision.retrieve() ? ragChatContextBuilder.build(message, sources) : "";
        Content userMsg = Content.fromParts(Part.fromText(message));
        Flowable<Event> events = runner.runAsync(userId, sessionId, userMsg,
                RunConfig.builder().build(), Map.of(RagChatContextBuilder.STATE_KEY, context));
        return new ChatStreamResultVO(events, sources);
    }

    private String recentAnswer(InMemoryRunner runner, String appName, String userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return "";
        try {
            Session session = runner.sessionService()
                    .getSession(appName, userId, sessionId, Optional.empty()).blockingGet();
            if (session == null) return "";
            List<Event> events = session.events();
            for (int i = events.size() - 1; i >= 0; i--) {
                Event event = events.get(i);
                if (!event.turnComplete().orElse(false) || !event.branch().orElse("").isEmpty()) continue;
                String answer = event.content().map(content -> content.parts().orElse(List.of()).stream()
                        .map(Part::text).flatMap(Optional::stream)
                        .reduce("", String::concat)).orElse("");
                if (!answer.isBlank()) return answer.substring(0, Math.min(answer.length(), 600));
            }
        } catch (Exception e) {
            log.debug("无法读取上轮回答用于 RAG 路由: {}", e.getMessage());
        }
        return "";
    }

    @Override
    public List<String> handleMessage(ChatCommandEntity chatCommandEntity) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(chatCommandEntity.getAgentId());

        if (null == aiAgentRegisterVO) {
            throw new AppException(ResponseCode.E0001.getCode());
        }

        List<Part> parts = new ArrayList<>();

        List<ChatCommandEntity.Content.Text> texts = chatCommandEntity.getTexts();
        if (null != texts && !texts.isEmpty()) {
            for (ChatCommandEntity.Content.Text text : texts) {
                parts.add(Part.fromText(text.getMessage()));
            }
        }

        List<ChatCommandEntity.Content.File> files = chatCommandEntity.getFiles();
        if (null != files && !files.isEmpty()) {
            for (ChatCommandEntity.Content.File file : files) {
                parts.add(Part.fromUri(file.getFileUri(), file.getMimeType()));
            }
        }

        List<ChatCommandEntity.Content.InlineData> inlineDatas = chatCommandEntity.getInlineDatas();
        if (null != inlineDatas && !inlineDatas.isEmpty()) {
            for (ChatCommandEntity.Content.InlineData inlineData : inlineDatas) {
                parts.add(Part.fromBytes(inlineData.getBytes(), inlineData.getMimeType()));
            }
        }

        Content content = Content.builder().role("user").parts(parts).build();

        // 获取运行体
        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Flowable<Event> events = runner.runAsync(chatCommandEntity.getUserId(), chatCommandEntity.getSessionId(), content);

        List<String> outputs = new ArrayList<>();
        events.blockingForEach(event -> outputs.add(event.stringifyContent()));

        return outputs;
    }

}
