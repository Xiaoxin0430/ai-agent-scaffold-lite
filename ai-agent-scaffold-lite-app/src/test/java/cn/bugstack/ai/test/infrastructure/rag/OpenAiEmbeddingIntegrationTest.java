package cn.bugstack.ai.test.infrastructure.rag;

import cn.bugstack.ai.config.RagEmbeddingConfig;
import cn.bugstack.ai.config.RagEmbeddingProperties;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

/**
 * 真实 Embedding 接口冒烟测试，默认跳过，避免日常测试消耗外部额度。
 *
 * @author xiaoxin
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = RagEmbeddingConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class OpenAiEmbeddingIntegrationTest {

    @Resource
    private RagEmbeddingProperties properties;

    @Resource
    @Qualifier("ragEmbeddingModel")
    private EmbeddingModel embeddingModel;

    @Test
    public void test_real_embedding_api() {
        Assume.assumeTrue(Boolean.parseBoolean(
                System.getProperty("rag.embedding.live-test", "false")));

        float[] vector = embeddingModel.embed("Spring AI RAG 向量化连通性测试");

        Assert.assertNotNull(vector);
        Assert.assertEquals(properties.getDimensions(), vector.length);
        Assert.assertTrue(vector.length > 0);
    }

}
