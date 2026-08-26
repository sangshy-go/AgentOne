package com.agentone.knowledge.service;

import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.knowledge.chunk.ChunkService;
import com.agentone.knowledge.entity.DocumentChunkDO;
import com.agentone.knowledge.entity.DocumentDO;
import com.agentone.knowledge.entity.KnowledgeBaseDO;
import com.agentone.knowledge.entity.ModelDO;
import com.agentone.knowledge.entity.ModelProviderDO;
import com.agentone.knowledge.mapper.DocumentChunkMapper;
import com.agentone.knowledge.mapper.DocumentMapper;
import com.agentone.knowledge.mapper.KnowledgeBaseMapper;
import com.agentone.knowledge.mapper.ModelMapper;
import com.agentone.knowledge.mapper.ModelProviderMapper;
import com.agentone.knowledge.vector.VectorStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档处理器：负责文档的分块 + 向量化 + 入库。
 *
 * 独立为组件的原因：
 * 1. 向量化需分批调用外部 Embedding API，耗时可能超过前端 HTTP 超时，必须异步执行；
 * 2. {@code @Async} 通过 Spring 代理生效，同类自调用会失效，故从 KnowledgeServiceImpl 拆出。
 *
 * upload 与 retry 均复用本组件，处理失败时把文档置为 error 状态（前端轮询可见，并支持重试）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentProcessor {

    private final DocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentChunkMapper chunkMapper;
    private final ChunkService chunkService;
    private final VectorStoreService vectorStoreService;
    private final ModelMapper modelMapper;
    private final ModelProviderMapper modelProviderMapper;

    /**
     * 异步处理文档：分块 + 向量化 + 更新状态。
     * 文档原始文本（rawContent）须在调用前已落库，本方法从 DB 重新读取，
     * 避免依赖请求线程中的 MultipartFile（请求结束后临时文件会被清理）。
     */
    @Async("documentProcessExecutor")
    public void processAsync(String documentId, String knowledgeId) {
        DocumentDO doc = documentMapper.selectById(documentId);
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(knowledgeId);
        if (doc == null || kb == null) {
            log.warn("文档异步处理跳过：文档或知识库不存在 docId={} kbId={}", documentId, knowledgeId);
            return;
        }

        String rawText = doc.getRawContent();
        if (rawText == null || rawText.isBlank()) {
            markError(doc, "原文内容为空，无法处理");
            return;
        }

        try {
            processRawContent(doc, kb, rawText);
        } catch (Exception e) {
            log.error("文档异步处理失败: docId={}, error={}", doc.getId(), e.getMessage(), e);
            markError(doc, e.getMessage() != null ? e.getMessage() : "处理失败");
        }
    }

    /**
     * 将原始文本分块 + 向量化 + 入库（upload 和 retry 复用）
     */
    public void processRawContent(DocumentDO doc, KnowledgeBaseDO kb, String rawText) {
        String knowledgeId = kb.getId();
        // 兜底默认值统一取 ChunkService 常量（与 DB 默认 400/60 一致），避免代码与 SQL 默认值分叉
        String strategy = kb.getChunkStrategy() != null ? kb.getChunkStrategy() : ChunkService.DEFAULT_STRATEGY;
        int chunkSize = kb.getChunkSize() != null ? kb.getChunkSize() : ChunkService.DEFAULT_CHUNK_SIZE;
        int overlap = kb.getChunkOverlap() != null ? kb.getChunkOverlap() : ChunkService.DEFAULT_CHUNK_OVERLAP;

        List<String> rawChunks = chunkService.splitByStrategy(rawText, strategy, chunkSize, overlap);
        List<String> chunks = ChunkService.STRATEGY_BY_TITLE.equals(strategy)
                ? rawChunks : injectHeadingContext(rawChunks);

        // 分块入库
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunkDO chunk = new DocumentChunkDO();
            chunk.setDocumentId(doc.getId());
            chunk.setContent(chunks.get(i));
            chunk.setChunkIndex(i);
            chunk.setMetadata("{}");
            chunkMapper.insert(chunk);
        }

        // 分批向量化（失败时回滚已入库的 chunk，避免孤儿数据）
        ModelDO embeddingModel = resolveEmbeddingModel(kb.getEmbeddingModelId());
        ModelProviderDO provider = resolveProvider(embeddingModel);

        try {
            List<DocumentChunkDO> allChunks = chunkMapper.selectList(
                    new LambdaQueryWrapper<DocumentChunkDO>()
                            .eq(DocumentChunkDO::getDocumentId, doc.getId())
                            .orderByAsc(DocumentChunkDO::getChunkIndex)
            );
            // 批大小取 10（最低公共上限）：DashScope text-embedding-v3/v4 单次请求最多 10 条文本，
            // 超过会报 "batch size is invalid, it should not be larger than 10"；OpenAI 等上限更宽，取 10 全兼容
            int batchSize = 10;
            for (int i = 0; i < allChunks.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allChunks.size());
                List<Document> batch = new ArrayList<>();
                for (int j = i; j < end; j++) {
                    DocumentChunkDO chunk = allChunks.get(j);
                    Map<String, Object> metadata = Map.of(
                            "documentId", doc.getId(),
                            "knowledgeId", knowledgeId,
                            "chunkIndex", chunk.getChunkIndex()
                    );
                    batch.add(new Document(chunk.getId(), chunk.getContent(), metadata));
                }
                vectorStoreService.saveBatchWithProvider(batch, provider, embeddingModel);
            }
        } catch (Exception e) {
            log.error("向量化失败，回滚已入库的 chunk 与向量: docId={}, error={}", doc.getId(), e.getMessage(), e);
            // 先按 chunk id 删除已写入的向量，避免向量表残留孤儿向量（仅靠删 chunk 行会漏删向量）
            try {
                List<DocumentChunkDO> storedChunks = chunkMapper.selectList(
                        new LambdaQueryWrapper<DocumentChunkDO>()
                                .eq(DocumentChunkDO::getDocumentId, doc.getId()));
                for (DocumentChunkDO c : storedChunks) {
                    try {
                        vectorStoreService.deleteWithProvider(c.getId(), provider, embeddingModel);
                    } catch (Exception ex) {
                        log.debug("回滚删除向量失败（可忽略）: chunkId={}, error={}", c.getId(), ex.getMessage());
                    }
                }
            } catch (Exception ex) {
                log.warn("查询分块用于回滚向量失败: docId={}, error={}", doc.getId(), ex.getMessage());
            }
            // 再删除已入库的 chunk 行
            chunkMapper.delete(
                    new LambdaQueryWrapper<DocumentChunkDO>()
                            .eq(DocumentChunkDO::getDocumentId, doc.getId())
            );
            throw e;
        }

        // 更新文档状态
        doc.setChunkCount(chunks.size());
        doc.setStatus("ready");
        documentMapper.updateById(doc);

        // 重算知识库统计（E10：从 DB 计数，与同步路径口径一致，避免累加漂移）
        recomputeKbCounts(kb);
    }

    /**
     * 从数据库重算知识库的文档数与分块数（与 KnowledgeServiceImpl.recomputeKbCounts 同口径）。
     */
    private void recomputeKbCounts(KnowledgeBaseDO kb) {
        List<DocumentDO> docs = documentMapper.selectList(
                new LambdaQueryWrapper<DocumentDO>()
                        .eq(DocumentDO::getKnowledgeId, kb.getId())
                        .select(DocumentDO::getChunkCount));
        int chunkCount = docs.stream()
                .mapToInt(d -> d.getChunkCount() != null ? d.getChunkCount() : 0)
                .sum();
        kb.setDocCount(docs.size());
        kb.setChunkCount(chunkCount);
        knowledgeBaseMapper.updateById(kb);
    }

    /**
     * 解析 embedding 模型：通过 embedding_model_id 查询 model 表。
     * 未配置或模型不存在时直接抛异常，不做兜底。
     */
    public ModelDO resolveEmbeddingModel(String embeddingModelId) {
        // 知识库的 Embedding 模型创建后不可变，也没有事后配置入口，
        // 因此错误文案直接给出唯一可行路径：删除并重建知识库，避免用户找不到"配置"入口困惑
        if (embeddingModelId == null || embeddingModelId.isBlank()) {
            throw new BusinessException(6008, "知识库未绑定 Embedding 模型（历史数据），请删除该知识库并重新创建");
        }
        ModelDO model = modelMapper.selectById(embeddingModelId);
        if (model == null) {
            throw new BusinessException(6008, "知识库绑定的 Embedding 模型已被删除，无法恢复，请删除该知识库并重新创建");
        }
        return model;
    }

    /**
     * 解析 embedding 模型关联的服务商配置
     */
    public ModelProviderDO resolveProvider(ModelDO embeddingModel) {
        ModelProviderDO provider = modelProviderMapper.selectById(embeddingModel.getProviderId());
        if (provider == null) {
            throw new BusinessException(6008, "Embedding 模型关联的服务商配置不存在，请检查模型服务商设置");
        }
        // S4 延伸：Embedding 模型供应商必须属于当前工作空间，
        // 防止通过知识库 embedding_model_id 引用他人供应商，消耗他人 API Key（跨租户费用转嫁）。
        String currentWs = RuntimeContext.getWorkspaceId();
        if (currentWs != null && provider.getWorkspaceId() != null
                && !currentWs.equals(provider.getWorkspaceId())) {
            throw new BusinessException(6012, "当前工作空间无权使用该模型供应商，请选择本空间的 Embedding 模型");
        }
        return provider;
    }

    private void markError(DocumentDO doc, String message) {
        doc.setStatus("error");
        doc.setErrorMsg(message != null
                ? message.substring(0, Math.min(message.length(), 2000))
                : "处理失败");
        documentMapper.updateById(doc);
    }

    /**
     * 为每个 chunk 注入所属标题上下文
     *
     * 原理：顺序遍历 chunk，如果 chunk 以 Markdown 标题开头，
     * 则将其作为后续 chunk 的上下文前缀，帮助 LLM 理解内容所属章节。
     *
     * 示例：
     *   chunk1: "## 退款政策\n用户可在7天内..." → "[退款政策] 用户可在7天内..."
     *   chunk2: "退款金额为实际支付金额..."     → "[退款政策] 退款金额为..."
     */
    private List<String> injectHeadingContext(List<String> chunks) {
        if (chunks.isEmpty()) {
            return chunks;
        }

        List<String> result = new ArrayList<>(chunks.size());
        String currentHeading = "";

        for (String chunk : chunks) {
            String firstLine = chunk.contains("\n")
                    ? chunk.substring(0, chunk.indexOf('\n')).trim()
                    : chunk.trim();

            // 检测 Markdown 标题（# / ## / ### / ####）
            boolean isHeadingChunk = firstLine.matches("^#{1,4}\\s+.+");
            if (isHeadingChunk) {
                currentHeading = firstLine.replaceFirst("^#+\\s*", "");
            }

            // 首个标题 chunk 本身已包含该标题（形如 "## 标题\n正文"），再前缀会得到重复的
            // "[标题] ## 标题..."。故当本 chunk 就是定义该标题的 chunk，或其正文已以标题文本开头时，
            // 跳过前缀，仅为不含标题的后续 chunk 注入上下文。
            if (!currentHeading.isEmpty() && !isHeadingChunk && !chunk.startsWith(currentHeading)) {
                result.add("[" + currentHeading + "] " + chunk);
            } else {
                result.add(chunk);
            }
        }
        return result;
    }
}
