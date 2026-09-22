package cn.bugstack.ai.test.app;

import cn.bugstack.ai.config.RagVectorStoreConfig;
import cn.bugstack.ai.config.RagVectorStoreProperties;
import cn.bugstack.ai.domain.rag.model.valobj.SimilaritySearchConfigVO;
import org.junit.Assert;
import org.junit.Test;

/**
 * RAG 向量存储配置测试。
 *
 * @author xiaoxin
 */
public class RagVectorStoreConfigTest {

    @Test
    public void test_build_similarity_search_config() {
        RagVectorStoreProperties properties = new RagVectorStoreProperties();
        properties.setDefaultTopK(6);
        properties.setMaxTopK(30);
        properties.setDefaultMinSimilarity(0.35d);

        SimilaritySearchConfigVO config = new RagVectorStoreConfig()
                .similaritySearchConfig(properties);

        Assert.assertEquals(Integer.valueOf(6), config.getDefaultTopK());
        Assert.assertEquals(Integer.valueOf(30), config.getMaxTopK());
        Assert.assertEquals(Double.valueOf(0.35d), config.getDefaultMinSimilarity());
    }

}
