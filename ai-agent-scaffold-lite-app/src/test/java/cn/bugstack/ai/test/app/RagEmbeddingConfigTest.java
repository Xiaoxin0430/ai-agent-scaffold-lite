package cn.bugstack.ai.test.app;

import cn.bugstack.ai.config.RagEmbeddingProperties;
import cn.bugstack.ai.config.RagEmbeddingConfig;
import cn.bugstack.ai.domain.rag.model.valobj.EmbeddingConfigVO;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

/**
 * RAG Embedding 启动配置测试。
 *
 * @author xiaoxin
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = RagEmbeddingConfig.class,
        properties = "ALiYun_API=test-key",
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class RagEmbeddingConfigTest {

    @Resource
    private RagEmbeddingProperties properties;

    @Resource
    private EmbeddingConfigVO embeddingConfig;

    @Resource
    @Qualifier("ragEmbeddingModel")
    private EmbeddingModel embeddingModel;

    @Test
    public void test_embedding_configuration_is_loaded() {
        Assert.assertNotNull(embeddingModel);
        Assert.assertEquals(properties.getModel(), embeddingConfig.getModel());
        Assert.assertEquals(properties.getDimensions(), embeddingConfig.getDimensions());
        Assert.assertEquals(properties.getBatchSize(), embeddingConfig.getBatchSize());
        Assert.assertEquals(properties.getVersion(), embeddingConfig.getEmbeddingVersion());
        Assert.assertFalse(properties.getBaseUrl().isBlank());
        Assert.assertFalse(properties.getEmbeddingsPath().isBlank());
    }

}
