package cn.bugstack.ai.test.domain.agent;

import cn.bugstack.ai.domain.agent.service.chat.RagChatContextBuilder;
import cn.bugstack.ai.domain.rag.model.entity.DocumentChunkEntity;
import cn.bugstack.ai.domain.rag.model.valobj.SimilarDocumentChunkVO;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

public class RagChatContextBuilderTest {

    @Test
    public void test_sources_and_citations_use_the_same_scope() {
        RagChatContextBuilder builder = new RagChatContextBuilder();
        ReflectionTestUtils.setField(builder, "maxContextCharacters", 4);
        List<SimilarDocumentChunkVO> selected = builder.selectSources(List.of(source("报销四天", "a.txt"), source("其它内容", "b.txt")));

        Assert.assertEquals(1, selected.size());
        String prompt = builder.build("报销期限？", selected);
        Assert.assertTrue(prompt.contains("[S1] 文件：a.txt"));
        Assert.assertFalse(prompt.contains("[S2]"));
        Assert.assertTrue(prompt.contains("报销期限？"));
    }

    @Test
    public void test_no_match_explicitly_reports_missing_evidence() {
        RagChatContextBuilder builder = new RagChatContextBuilder();
        Assert.assertTrue(builder.build("报销期限？", List.of()).contains("未找到相关资料"));
    }

    private SimilarDocumentChunkVO source(String content, String fileName) {
        return SimilarDocumentChunkVO.builder()
                .chunk(DocumentChunkEntity.builder().content(content).build())
                .fileName(fileName)
                .build();
    }
}
