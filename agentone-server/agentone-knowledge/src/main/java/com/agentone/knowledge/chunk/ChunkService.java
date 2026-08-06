package com.agentone.knowledge.chunk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文本分块服务
 *
 * 核心分块委托给 Spring AI 的 TokenTextSplitter（基于 token 而非字符，中英文一致）。
 * 本服务在此基础上提供两种结构化策略：
 * - by-length:    直接委托 TokenTextSplitter（默认）
 * - by-title:     按 Markdown 标题切分 section，超长 section 回退到 TokenTextSplitter
 * - by-paragraph: 按段落合并至 chunkSize，超长段落回退到 TokenTextSplitter
 *
 * overlap 作为后处理步骤：将前一个 chunk 的尾部文本拼接到下一个 chunk 头部。
 */
@Slf4j
@Service
public class ChunkService {

    public static final String STRATEGY_BY_LENGTH = "by-length";
    public static final String STRATEGY_BY_TITLE = "by-title";
    public static final String STRATEGY_BY_PARAGRAPH = "by-paragraph";

    /** TokenTextSplitter 内部参数：小于此字符数的 chunk 会被合并到相邻 chunk */
    private static final int MIN_CHUNK_SIZE_CHARS = 100;
    /** 小于此长度的文本不会生成 embedding */
    private static final int MIN_CHUNK_LENGTH_TO_EMBED = 10;
    /** 单文档最大 chunk 数（防止异常大文档） */
    private static final int MAX_NUM_CHUNKS = 10000;

    /**
     * 按策略分块（入口方法）
     *
     * @param text      原始文本
     * @param strategy  分块策略（by-length / by-title / by-paragraph）
     * @param chunkSize 目标 chunk 大小（token 数）
     * @param overlap   相邻 chunk 重叠字符数（后处理，0 表示不重叠）
     */
    public List<String> splitByStrategy(String text, String strategy, int chunkSize, int overlap) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        validateParams(chunkSize, overlap);

        String s = (strategy != null) ? strategy : STRATEGY_BY_LENGTH;
        List<String> chunks = switch (s) {
            case STRATEGY_BY_TITLE -> splitByTitle(text, chunkSize);
            case STRATEGY_BY_PARAGRAPH -> splitByParagraph(text, chunkSize);
            default -> splitByToken(text, chunkSize);
        };

        // overlap 后处理
        if (overlap > 0 && chunks.size() > 1) {
            chunks = applyOverlap(chunks, overlap);
        }

        log.debug("分块完成: strategy={}, 原文长度={}, 分块数={}, chunkSize(tokens)={}, overlap={}",
                s, text.length(), chunks.size(), chunkSize, overlap);
        return chunks;
    }

    // ==================== 核心分块：委托 Spring AI TokenTextSplitter ====================

    /**
     * 基于 token 的分块（by-length 策略）
     * 使用 Spring AI TokenTextSplitter，按 token 数切分并自动在句子/段落边界断开。
     */
    private List<String> splitByToken(String text, int chunkSize) {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withMinChunkSizeChars(MIN_CHUNK_SIZE_CHARS)
                .withMinChunkLengthToEmbed(MIN_CHUNK_LENGTH_TO_EMBED)
                .withMaxNumChunks(MAX_NUM_CHUNKS)
                .withKeepSeparator(true)
                .build();

        // Spring AI 1.0.x 中 splitText 为 protected，需通过公共入口 apply(Document) 调用，
        // 再提取每个分块的文本。apply 内部即委托 splitText，分块结果完全一致。
        return splitter.apply(List.of(new Document(text)))
                .stream()
                .map(Document::getText)
                .collect(Collectors.toList());
    }

    // ==================== 结构化策略 ====================

    /**
     * 按 Markdown 标题分块（by-title）
     *
     * 规则：
     * 1. 遇到 # / ## / ### / #### 开头的行 → 开启新 section
     * 2. 每个 section 作为一个 chunk
     * 3. 若 section 超过 chunkSize token，回退到 TokenTextSplitter 继续切分（保留标题前缀）
     * 4. 没有标题的开头内容并入第一个带标题的 chunk
     */
    private List<String> splitByTitle(String text, int chunkSize) {
        String[] lines = text.split("\n");
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            if (isMarkdownHeading(line)) {
                if (current.length() > 0) {
                    sections.add(current.toString().trim());
                }
                current.setLength(0);
                current.append(line).append("\n");
            } else {
                current.append(line).append("\n");
            }
        }
        if (current.length() > 0) {
            sections.add(current.toString().trim());
        }

        // 若整个文本都没有标题，回退到 by-length
        if (sections.size() <= 1 && !text.matches("(?s).*^#{1,4}\\s+.*")) {
            return splitByToken(text, chunkSize);
        }

        // 对超长 section 做二次切分（保留标题前缀）
        List<String> chunks = new ArrayList<>();
        for (String section : sections) {
            String trimmed = section.trim();
            if (trimmed.isEmpty()) continue;

            // 用 TokenTextSplitter 判断是否需要二次切分
            List<String> subChunks = splitByToken(trimmed, chunkSize);
            if (subChunks.size() <= 1) {
                chunks.add(trimmed);
            } else {
                // 超长 section：取出标题行，子 chunk 保留标题前缀
                int firstNewline = trimmed.indexOf('\n');
                if (firstNewline > 0 && isMarkdownHeading(trimmed.substring(0, firstNewline).trim())) {
                    String heading = trimmed.substring(0, firstNewline + 1);
                    String body = trimmed.substring(firstNewline + 1).trim();
                    List<String> bodyChunks = splitByToken(body, chunkSize);
                    for (String sc : bodyChunks) {
                        chunks.add(heading + sc);
                    }
                } else {
                    chunks.addAll(subChunks);
                }
            }
        }

        return chunks;
    }

    /**
     * 按段落分块（by-paragraph）
     *
     * 规则：
     * 1. 以 \n\n 为分隔切出段落
     * 2. 从小到大合并段落，直到再加一段就超过 chunkSize（按字符估算）
     * 3. 单个段落本身超长时，回退到 TokenTextSplitter 切分
     */
    private List<String> splitByParagraph(String text, int chunkSize) {
        // 粗略估算：1 token ≈ 2 中文字符 或 4 英文字符，取中间值 3
        int approxChunkChars = chunkSize * 3;

        String[] paragraphs = text.split("\n\n+");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String p : paragraphs) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) continue;

            // 单段超长 → TokenTextSplitter 切分后落袋
            if (trimmed.length() > approxChunkChars) {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                chunks.addAll(splitByToken(trimmed, chunkSize));
                continue;
            }

            // 合并后超了 → 先落袋当前累积，再开始新累积
            int nextLen = current.length() + (current.length() > 0 ? 2 : 0) + trimmed.length();
            if (nextLen > approxChunkChars && current.length() > 0) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(trimmed);
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }

    // ==================== 辅助方法 ====================

    /**
     * overlap 后处理：将前一个 chunk 的尾部 overlap 个字符拼接到下一个 chunk 头部。
     * 尽量在自然断句处截取（句号、换行）。
     */
    private List<String> applyOverlap(List<String> chunks, int overlap) {
        List<String> result = new ArrayList<>(chunks.size());
        result.add(chunks.get(0));

        for (int i = 1; i < chunks.size(); i++) {
            String prev = chunks.get(i - 1);
            String tail = extractTail(prev, overlap);
            result.add(tail + chunks.get(i));
        }
        return result;
    }

    /**
     * 从文本尾部提取 overlap 个字符，尽量在句子边界截取
     */
    private String extractTail(String text, int overlap) {
        if (text.length() <= overlap) {
            return text;
        }
        int start = text.length() - overlap;

        // 尝试在句子边界截取（向后找到第一个断句点）
        for (int i = start; i < Math.min(start + 50, text.length()); i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '.' || c == '\n' || c == '！' || c == '？') {
                return text.substring(i + 1).stripLeading();
            }
        }
        // 找不到断句点，直接截取
        return text.substring(start).stripLeading();
    }

    private void validateParams(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize 必须大于 0");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("overlap 不能为负数");
        }
    }

    private boolean isMarkdownHeading(String line) {
        if (line == null) return false;
        return line.matches("^#{1,4}\\s+.+");
    }
}
