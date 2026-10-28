package cn.bugstack.ai.test.boundary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 调用已在 IDEA 启动的应用，检验同一会话经过多轮话题干扰后是否仍记得首轮关注点，
 * 并能区分历史配置快照与当前实时交易状态。
 * 显式设置 -Dchat.long-dialog.live-test=true 才会调用真实模型和 Embedding 服务。
 */
class LongConversationTopicDriftLiveTest {

    private static final String AGENT_ID = "100004";
    private static final String FOCUS_CODE = "蓝鹊-7319";
    private static final String OPENING_QUESTION = """
            我最担心的边界是：不能把活动配置快照、核对时的0个团，当成我现在的团位或退款状态。
            为了检验长对话记忆，我把这个关注点叫作“蓝鹊-7319”。请先确认你理解；
            这个代号只是我的关注点，不是活动规则。后面我会再问你。
            """;
    private static final List<String> QUESTIONS = List.of(
            "三个人一起买，239元是三个人合计的价钱吗？请区分成团人数与每单试算价。",
            "资料写着15分钟。这是拍摄时长、整个活动时长，还是单个团的有效时长？",
            "活动和优惠没有设置人群标签，是不是所有人都一定能参加？",
            "顺便讲讲 Java 线程池的核心参数。",
            "资料说核对时活动生效，所以现在一定仍然生效，对吗？",
            "如果我的团没凑满3人，你能保证订单15分钟后一定关闭、款项马上到账吗？",
            "先放下活动，写一首关于宇宙的短诗。",
            "单用户参与次数限制写1次，就能直接断定我当前有参团资格吗？",
            "你能直接帮我开团并付款吗？",
            "推荐一款适合拍照的相机。",
            "已付款未成团和已付款已成团的退单判断能完全一样吗？能保证我这笔订单退成功吗？",
            "文档核对时的0个团，是不是说明我现在的团还差3人？"
    );
    private static final String FINAL_QUESTION =
            "回到最开始的讨论：我给最担心的边界起了什么代号？它代表什么？"
            + "文档核对时的团数能否证明我现在的团还差几人？请引用资料。";
    private static final String DOCUMENT = """
            # 毕业照基础套餐三人团活动配置快照（边界测试资料）
            核对时间为2026-10-22 20:24。这是当时的配置快照，不是实时订单或团位查询结果。
            商品原价为每单299元，组队优惠为每单直减60元，试算支付金额为每单239元；
            三人是成团目标，不表示三单合计239元。实际结算金额以交易页面为准。
            单个团的有效时长配置为15分钟，不是拍摄时长，也不是整个活动的持续时间。
            单用户参与次数限制配置为1次，但实际参与资格还需要业务系统校验。
            核对时活动记录为生效状态，活动和优惠没有设置人群标签；这些信息不能代替现在的活动状态或资格校验。
            项目存在未付款未成团、已付款未成团、已付款已成团等不同退单路径。
            某个订单能否退单、何时关闭、退款是否成功和到账时间都不能凭这份资料确定。
            核对时组队记录数为0，这是历史瞬时值，不能用于推断现在的团位或某个用户的订单状态。
            活动助手不能替用户开团、参团、付款或退款，也不能查询实时团位和订单。
            """;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final String baseUrl = System.getProperty("chat.long-dialog.base-url", "http://127.0.0.1:8091")
            .replaceAll("/+$", "");
    private final String runId = UUID.randomUUID().toString();
    private final String userId = "long-dialog-" + runId;
    private final String documentId = "long-dialog-" + runId;
    private boolean attemptedIndex;

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void staysOnTopicAfterLongConversation() throws Exception {
        assumeTrue(Boolean.getBoolean("chat.long-dialog.live-test"),
                "Real HTTP/model test is opt-in");

        int turns = Integer.getInteger("chat.long-dialog.turns", QUESTIONS.size());
        assertTrue(turns >= 1 && turns <= QUESTIONS.size(), "turns must be between 1 and 12");

        attemptedIndex = true;
        JsonNode index = postMultipart("/api/v1/rag/documents/" + documentId + "/index");
        assertEquals("0000", index.path("code").asText(), "test document indexing failed: " + index);
        assertTrue(index.path("data").path("chunkCount").asInt() > 0,
                "test document did not produce any chunks");

        JsonNode session = postJson("/api/v1/create_session",
                Map.of("agentId", AGENT_ID, "userId", userId));
        assertEquals("0000", session.path("code").asText(), "create_session failed: " + session);
        String sessionId = session.path("data").path("sessionId").asText();
        assertFalse(sessionId.isBlank(), "create_session returned no sessionId");
        System.out.printf("Long-dialog test userId=%s documentId=%s sessionId=%s%n",
                userId, documentId, sessionId);

        ChatResult opening = chat(sessionId, OPENING_QUESTION);
        assertTrue(opening.completed(), "opening turn did not finish");
        assertFalse(opening.answer().isBlank(), "opening turn has an empty answer");
        System.out.printf("Opening question: %s%nOpening answer: %s%n",
                OPENING_QUESTION, opening.answer());

        for (int i = 0; i < turns; i++) {
            ChatResult result = chat(sessionId, QUESTIONS.get(i));
            assertTrue(result.completed(), "turn " + (i + 1) + " did not finish");
            assertFalse(result.answer().isBlank(), "turn " + (i + 1) + " has an empty answer");
            System.out.printf("Turn %02d question: %s%nTurn %02d answer: %s%n",
                    i + 1, QUESTIONS.get(i), i + 1, result.answer());
        }

        ChatResult finalResult = chat(sessionId, FINAL_QUESTION);
        System.out.printf("Final question: %s%nFinal sources: %d%nFinal answer: %s%n",
                FINAL_QUESTION, finalResult.sourceCount(), finalResult.answer());

        assertTrue(finalResult.completed(), "final turn did not finish");
        assertTrue(finalResult.sourceCount() > 0, "final turn did not retrieve the test document");
        assertTrue(finalResult.answer().contains(FOCUS_CODE),
                "final answer forgot the focus code given only in the opening turn");
        assertTrue(finalResult.answer().contains("快照") && finalResult.answer().contains("实时"),
                "final answer did not distinguish the historical snapshot from live status");
        assertTrue(finalResult.answer().matches("(?s).*\\[S\\d+\\].*"),
                "final answer did not cite its retrieved source");
    }

    @AfterEach
    void removeTemporaryDocument() throws Exception {
        if (!attemptedIndex) return;

        Properties settings = new Properties();
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.isRegularFile(directory.resolve(".env"))) {
            directory = directory.getParent();
        }
        if (directory != null) {
            try (InputStream input = Files.newInputStream(directory.resolve(".env"))) {
                settings.load(input);
            }
        }
        String url = setting(settings, "RAG_DB_URL");
        String username = setting(settings, "RAG_DB_USERNAME");
        String password = setting(settings, "RAG_DB_PASSWORD");
        assertFalse(url.isBlank() || username.isBlank() || password.isBlank(),
                "RAG_DB_URL, RAG_DB_USERNAME and RAG_DB_PASSWORD are needed for test cleanup");

        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.prepareStatement(
                     "DELETE FROM rag_document WHERE document_id = ? AND owner_user_id = ?")) {
            statement.setString(1, documentId);
            statement.setString(2, userId);
            int deleted = statement.executeUpdate();
            System.out.printf("Temporary test document cleanup: %d row(s), documentId=%s%n",
                    deleted, documentId);
        }
    }

    private String setting(Properties settings, String key) {
        String fromEnvironment = System.getenv(key);
        return fromEnvironment != null && !fromEnvironment.isBlank()
                ? fromEnvironment : settings.getProperty(key, "");
    }

    private ChatResult chat(String sessionId, String question) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/chat_stream"))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/x-ndjson")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                        "agentId", AGENT_ID,
                        "userId", userId,
                        "sessionId", sessionId,
                        "message", question)), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode(), "chat_stream HTTP error: " + response.body());

        StringBuilder draft = new StringBuilder();
        String answer = "";
        int sourceCount = 0;
        boolean completed = false;
        for (String line : response.body().lines().toList()) {
            if (line.isBlank()) continue;
            JsonNode frame = mapper.readTree(line);
            String type = frame.path("type").asText();
            if ("error".equals(type)) {
                throw new AssertionError("chat_stream error: " + frame.path("message").asText());
            }
            if ("done".equals(type)) completed = true;
            if ("sources".equals(type)) sourceCount = frame.path("sources").size();
            if (!"message".equals(type) || !frame.path("branch").asText().isEmpty()) continue;
            String text = frame.path("text").asText();
            if (text.isBlank()) continue;
            if (frame.path("partial").asBoolean()) {
                draft.append(text);
                answer = draft.toString();
            } else {
                draft.setLength(0);
                draft.append(text);
                answer = text;
            }
        }
        return new ChatResult(answer, sourceCount, completed);
    }

    private JsonNode postJson(String path, Map<String, String> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        return sendJson(request);
    }

    private JsonNode postMultipart(String path) throws Exception {
        String boundary = "----long-dialog-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writePart(body, boundary, "userId", null, userId);
        writePart(body, boundary, "file", "long-dialog-test.txt", DOCUMENT);
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return sendJson(request);
    }

    private void writePart(ByteArrayOutputStream body, String boundary, String name,
                           String fileName, String value) throws Exception {
        String disposition = "Content-Disposition: form-data; name=\"" + name + "\"";
        if (fileName != null) disposition += "; filename=\"" + fileName + "\"";
        String header = "--" + boundary + "\r\n" + disposition + "\r\n"
                + (fileName == null ? "" : "Content-Type: text/plain; charset=utf-8\r\n")
                + "\r\n";
        body.write(header.getBytes(StandardCharsets.UTF_8));
        body.write(value.getBytes(StandardCharsets.UTF_8));
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode sendJson(HttpRequest request) throws Exception {
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode(), "HTTP error: " + response.body());
        return mapper.readTree(response.body());
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    private record ChatResult(String answer, int sourceCount, boolean completed) {}
}
