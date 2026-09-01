package com.agentone.knowledge.chunk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ChunkService 分块逻辑单测（Q7：知识库模块回归保障）。
 *
 * 运行：mvn -pl agentone-knowledge test（依赖 spring-boot-starter-test，已在 pom 声明）。
 * 注：splitByToken 委托 Spring AI 的 TokenTextSplitter，为离线纯文本切分器，无需网络/API Key。
 */
class ChunkServiceTest {

    private final ChunkService chunkService = new ChunkService();

    @Test
    void splitByLength_returnsChunksForLongText() {
        String text = "这是一段用于测试分块的较长中文文本。".repeat(200);
        List<String> chunks = chunkService.splitByStrategy(text, ChunkService.STRATEGY_BY_LENGTH, 100, 0);
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty(), "长文本应产生至少一个分块");
    }

    @Test
    void splitByTitle_splitsOnHeadings() {
        String text = "# 标题一\n内容一\n\n## 标题二\n内容二\n\n# 标题三\n内容三";
        List<String> chunks = chunkService.splitByStrategy(text, ChunkService.STRATEGY_BY_TITLE, 200, 0);
        assertTrue(chunks.size() >= 3, "应至少按 3 个标题切成 3 段，实际：" + chunks.size());
        assertTrue(chunks.stream().anyMatch(c -> c.contains("标题一")));
        assertTrue(chunks.stream().anyMatch(c -> c.contains("标题二")));
        assertTrue(chunks.stream().anyMatch(c -> c.contains("标题三")));
    }

    @Test
    void splitByParagraph_mergesShortParagraphs() {
        String text = String.join("\n\n",
                "第一段内容较短。", "第二段内容较短。", "第三段内容较短。", "第四段内容较短。");
        List<String> chunks = chunkService.splitByStrategy(text, ChunkService.STRATEGY_BY_PARAGRAPH, 100, 0);
        // 短段落应被合并为一个 chunk
        assertEquals(1, chunks.size(), "短段落应合并为一个分块");
        assertTrue(chunks.get(0).contains("第一段") && chunks.get(0).contains("第四段"));
    }

    @Test
    void overlap_appendsTailToNextChunk() {
        String text = "这是第一段测试内容用于验证重叠逻辑。".repeat(50)
                + "\n\n" + "这是第二段测试内容用于验证重叠逻辑。".repeat(50);
        List<String> noOverlap = chunkService.splitByStrategy(text, ChunkService.STRATEGY_BY_PARAGRAPH, 100, 0);
        List<String> withOverlap = chunkService.splitByStrategy(text, ChunkService.STRATEGY_BY_PARAGRAPH, 100, 20);
        assertTrue(withOverlap.size() >= 2, "应至少产生 2 个分块");
        if (noOverlap.size() >= 2 && withOverlap.size() >= 2) {
            String tailOfPrev = noOverlap.get(0);
            tailOfPrev = tailOfPrev.substring(Math.max(0, tailOfPrev.length() - 20));
            String probe = tailOfPrev.substring(0, Math.min(10, tailOfPrev.length()));
            assertTrue(withOverlap.get(1).contains(probe),
                    "带 overlap 的后续分块应包含前一块尾部片段");
        }
    }

    @Test
    void emptyTextReturnsEmptyList() {
        assertTrue(chunkService.splitByStrategy("", ChunkService.STRATEGY_BY_LENGTH, 100, 0).isEmpty());
        assertTrue(chunkService.splitByStrategy(null, ChunkService.STRATEGY_BY_LENGTH, 100, 0).isEmpty());
    }

    @Test
    void invalidChunkSizeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> chunkService.splitByStrategy("文本", ChunkService.STRATEGY_BY_LENGTH, 0, 0));
    }

    @Test
    void negativeOverlapThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> chunkService.splitByStrategy("文本", ChunkService.STRATEGY_BY_LENGTH, 100, -1));
    }

    @Test
    void singleChunk_noOverlapApplied() {
        // 注意：文本必须 >= 10 字符（MIN_CHUNK_LENGTH_TO_EMBED），否则不会产出分块
        List<String> chunks = chunkService.splitByStrategy(
                "这是一段用于测试单分块场景的完整文本内容。", ChunkService.STRATEGY_BY_LENGTH, 1000, 20);
        assertEquals(1, chunks.size(), "单分块场景不应触发 overlap 处理");
    }

    @Test
    void textShorterThanEmbedThreshold_returnsEmpty() {
        // 设计约束：小于 10 字符的文本不生成 embedding，因此无分块产出
        assertTrue(chunkService.splitByStrategy("太短", ChunkService.STRATEGY_BY_LENGTH, 1000, 0).isEmpty());
    }
}
