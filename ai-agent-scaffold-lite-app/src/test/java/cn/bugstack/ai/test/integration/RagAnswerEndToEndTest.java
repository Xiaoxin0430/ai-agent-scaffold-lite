package cn.bugstack.ai.test.integration;

import cn.bugstack.ai.Application;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 文档上传到检索增强答案的真实 HTTP 端到端测试。
 * 默认跳过，显式开启后会访问 PostgreSQL、Embedding 和对话模型。
 *
 * @author xiaoxin
 */
public class RagAnswerEndToEndTest {

    @Test
    public void test_index_retrieve_and_generate_answer_through_http() throws Exception {
        Assume.assumeTrue(Boolean.parseBoolean(
                System.getProperty("rag.answer.live-test", "false")));

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                Application.class)
                .run("--spring.profiles.active=test", "--server.port=0")) {
            int port = ((ServletWebServerApplicationContext) context)
                    .getWebServer()
                    .getPort();
            String baseUrl = "http://127.0.0.1:" + port + "/api/v1/rag";
            RestTemplate restTemplate = new RestTemplate();

            JsonNode indexResponse = indexDocument(restTemplate, baseUrl);
            Assert.assertEquals("0000", indexResponse.path("code").asText());
            Assert.assertTrue(indexResponse.path("data").path("chunkCount").asInt() > 0);

            Map<String, Object> request = Map.of(
                    "query", "未单独列明城市时，普通员工住宿费用上限是多少？",
                    "topK", 5,
                    "minSimilarity", 0.0d,
                    "documentIds", List.of("rag-answer-live-test"));
            JsonNode answerResponse = restTemplate.postForObject(
                    baseUrl + "/answer", request, JsonNode.class);

            Assert.assertNotNull(answerResponse);
            Assert.assertEquals("0000", answerResponse.path("code").asText());
            Assert.assertFalse(answerResponse.path("data").path("answer").asText().isBlank());
            Assert.assertTrue(answerResponse.path("data").path("answer")
                    .asText().contains("500"));
            Assert.assertTrue(answerResponse.path("data").path("sources").size() > 0);
            Assert.assertEquals("rag-answer-live-test",
                    answerResponse.path("data").path("sources").get(0)
                            .path("documentId").asText());
        }
    }

    private JsonNode indexDocument(RestTemplate restTemplate,
                                   String baseUrl) throws Exception {
        byte[] content;
        try (InputStream inputStream = getClass().getResourceAsStream(
                "/rag/employee-expense-reimbursement-guide.txt")) {
            if (inputStream == null) {
                throw new IllegalStateException("test document does not exist");
            }
            content = inputStream.readAllBytes();
        }

        ByteArrayResource fileResource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "employee-expense-reimbursement-guide.txt";
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/documents/rag-answer-live-test/index",
                new HttpEntity<>(body, headers),
                JsonNode.class);
        return response.getBody();
    }

}
