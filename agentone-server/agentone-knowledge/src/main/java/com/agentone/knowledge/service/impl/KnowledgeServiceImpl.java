package com.agentone.knowledge.service.impl;

import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.knowledge.dto.BindKnowledgeDTO;
import com.agentone.knowledge.dto.KnowledgeBaseDTO;
import com.agentone.knowledge.entity.AgentKnowledgeBindingDO;
import com.agentone.knowledge.entity.DocumentChunkDO;
import com.agentone.knowledge.entity.DocumentDO;
import com.agentone.knowledge.entity.KnowledgeBaseDO;
import com.agentone.knowledge.entity.ModelDO;
import com.agentone.knowledge.entity.ModelProviderDO;
import com.agentone.knowledge.mapper.AgentKnowledgeBindingMapper;
import com.agentone.knowledge.mapper.DocumentChunkMapper;
import com.agentone.knowledge.mapper.DocumentMapper;
import com.agentone.knowledge.mapper.KnowledgeBaseMapper;
import com.agentone.knowledge.parser.DocumentParser;
import com.agentone.knowledge.service.DocumentProcessor;
import com.agentone.knowledge.service.KnowledgeService;
import com.agentone.knowledge.vector.VectorSearchResult;
import com.agentone.knowledge.vector.VectorStoreService;
import com.agentone.knowledge.vo.AgentKnowledgeBindingVO;
import com.agentone.knowledge.vo.DocumentVO;
import com.agentone.knowledge.vo.KnowledgeBaseVO;
import com.agentone.knowledge.vo.SearchResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.agentone.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    /** 允许上传的文档类型白名单（与 DocumentParser 支持范围、前端 accept 保持一致） */
    private static final Set<String> ALLOWED_DOC_TYPES =
            Set.of("txt", "md", "pdf", "doc", "docx", "html", "csv");

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final AgentKnowledgeBindingMapper bindingMapper;
    private final DocumentParser documentParser;
    private final VectorStoreService vectorStoreService;
    private final DocumentProcessor documentProcessor;

    // ==================== 知识库管理 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseVO createKnowledgeBase(KnowledgeBaseDTO dto) {
        KnowledgeBaseDO kb = new KnowledgeBaseDO();
        kb.setWorkspaceId(RuntimeContext.getWorkspaceId());
        kb.setName(dto.getName());
        kb.setDescription(dto.getDescription());
        kb.setDocCount(0);
        kb.setChunkCount(0);
        kb.setEmbeddingModel(dto.getEmbeddingModel() != null ? dto.getEmbeddingModel() : "text-embedding-3-small");
        kb.setEmbeddingModelId(dto.getEmbeddingModelId());
        kb.setChunkStrategy(dto.getChunkStrategy() != null ? dto.getChunkStrategy() : "by-length");
        kb.setChunkSize(dto.getChunkSize() != null ? dto.getChunkSize() : 512);
        kb.setChunkOverlap(dto.getChunkOverlap() != null ? dto.getChunkOverlap() : 50);
        kb.setCreatedAt(LocalDateTime.now());

        knowledgeBaseMapper.insert(kb);
        return toKnowledgeBaseVO(kb);
    }

    @Override
    public PageResult<KnowledgeBaseVO> listKnowledgeBases(Integer page, Integer size) {
        String wsId = RuntimeContext.getWorkspaceId();
        Page<KnowledgeBaseDO> p = new Page<>(page, size);
        Page<KnowledgeBaseDO> result = knowledgeBaseMapper.selectPage(p,
                new LambdaQueryWrapper<KnowledgeBaseDO>()
                        .eq(KnowledgeBaseDO::getWorkspaceId, wsId)
                        .orderByDesc(KnowledgeBaseDO::getCreatedAt));
        Page<KnowledgeBaseVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toKnowledgeBaseVO).collect(Collectors.toList()));
        return PageResult.of(voPage);
    }

    @Override
    public KnowledgeBaseVO getKnowledgeBase(String id) {
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(6001, "知识库不存在");
        }
        return toKnowledgeBaseVO(kb);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseVO updateKnowledgeBase(String id, KnowledgeBaseDTO dto) {
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(6001, "知识库不存在");
        }
        // 仅允许修改名称与描述，其余字段一律忽略（服务端强制，不信任客户端）：
        // - embeddingModel / embeddingModelId 创建后不可变：不同模型向量维度不同，切换会导致
        //   已有向量与查询向量空间不一致，检索直接失效甚至报错；
        // - 分块参数修改不会重切已有文档，改了只会让配置与实际数据脱节。
        // 与前端编辑弹窗"仅可编辑名称与描述"的约束保持一致。
        if (dto.getName() != null) kb.setName(dto.getName());
        if (dto.getDescription() != null) kb.setDescription(dto.getDescription());

        knowledgeBaseMapper.updateById(kb);
        return toKnowledgeBaseVO(kb);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeBase(String id) {
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(6001, "知识库不存在");
        }
        // 1. 删除 Agent 绑定关系（外键约束）
        bindingMapper.delete(
                new LambdaQueryWrapper<AgentKnowledgeBindingDO>()
                        .eq(AgentKnowledgeBindingDO::getKnowledgeId, id)
        );
        // 2. 删除关联文档和分块
        List<DocumentDO> docs = documentMapper.selectList(
                new LambdaQueryWrapper<DocumentDO>().eq(DocumentDO::getKnowledgeId, id)
        );
        for (DocumentDO doc : docs) {
            deleteDocument(doc.getId());
        }
        // 3. 删除知识库
        knowledgeBaseMapper.deleteById(id);
    }

    // ==================== 文档管理 ====================

    @Override
    public DocumentVO uploadDocument(String knowledgeId, MultipartFile file) {
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(knowledgeId);
        if (kb == null) {
            throw new BusinessException(6001, "知识库不存在");
        }

        // F4: 文件类型白名单校验，先挡掉非法类型，避免进入解析流程
        String type = getFileType(file.getOriginalFilename());
        if (!ALLOWED_DOC_TYPES.contains(type)) {
            throw new BusinessException(6009, "不支持的文件类型：" + (type.isEmpty() ? "未知" : type)
                    + "，仅支持 txt / md / pdf / doc / docx / html / csv");
        }

        // B10: 重复上传检测（同知识库下 文件名+大小 相同视为重复，避免重复向量与费用浪费）
        Long dupCount = documentMapper.selectCount(
                new LambdaQueryWrapper<DocumentDO>()
                        .eq(DocumentDO::getKnowledgeId, knowledgeId)
                        .eq(DocumentDO::getName, file.getOriginalFilename())
                        .eq(DocumentDO::getSize, file.getSize()));
        if (dupCount != null && dupCount > 0) {
            throw new BusinessException(6010, "该知识库已存在相同文档，请勿重复上传：" + file.getOriginalFilename());
        }

        // 前置校验：Embedding 模型必须配置，避免创建无效文档记录
        documentProcessor.resolveProvider(documentProcessor.resolveEmbeddingModel(kb.getEmbeddingModelId()));

        // 创建文档记录
        DocumentDO doc = new DocumentDO();
        doc.setKnowledgeId(knowledgeId);
        doc.setName(file.getOriginalFilename());
        doc.setType(type);
        doc.setSize(file.getSize());
        doc.setChunkCount(0);
        doc.setStatus("processing");
        doc.setCreatedAt(LocalDateTime.now());
        documentMapper.insert(doc);

        // 重算知识库统计（从 DB 计数，避免应用层累加漂移）
        recomputeKbCounts(kb);

        // 同步解析文档：MultipartFile 仅在请求线程内有效，必须在异步处理前完成解析并落库
        String text;
        try {
            text = documentParser.parse(file.getInputStream(), doc.getType());
        } catch (Exception e) {
            log.error("文档解析失败: docId={}, error={}", doc.getId(), e.getMessage(), e);
            markUploadError(doc, e.getMessage() != null ? e.getMessage() : "文档解析失败");
            return toDocumentVO(doc);
        }
        if (text == null || text.isBlank()) {
            markUploadError(doc, "文档解析结果为空");
            return toDocumentVO(doc);
        }

        // 保存原始文本（分块前），用于失败重试
        doc.setRawContent(text);
        documentMapper.updateById(doc);

        // 异步执行分块 + 向量化（耗时操作，避免阻塞 HTTP 请求导致前端超时）
        triggerAsyncProcessing(doc.getId(), kb.getId());

        return toDocumentVO(doc);
    }

    @Override
    public PageResult<DocumentVO> listDocuments(String knowledgeId, Integer page, Integer size) {
        Page<DocumentDO> p = new Page<>(page, size);
        Page<DocumentDO> result = documentMapper.selectPage(p,
                new LambdaQueryWrapper<DocumentDO>()
                        .eq(DocumentDO::getKnowledgeId, knowledgeId)
                        .orderByDesc(DocumentDO::getCreatedAt));
        Page<DocumentVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toDocumentVO).collect(Collectors.toList()));
        return PageResult.of(voPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVO retryDocument(String documentId) {
        DocumentDO doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new BusinessException(6003, "文档不存在");
        }
        if (!"error".equals(doc.getStatus())) {
            throw new BusinessException(6005, "只能重试处理失败的文档");
        }

        String rawText = doc.getRawContent();
        if (rawText == null || rawText.isBlank()) {
            throw new BusinessException(6006, "原文内容为空（可能是解析阶段失败），请重新上传文档");
        }

        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(doc.getKnowledgeId());
        if (kb == null) {
            throw new BusinessException(6001, "知识库不存在");
        }

        // 清理旧的分块和向量（避免重复累加）
        cleanupDocumentChunks(doc, kb);

        // 重置状态
        doc.setStatus("processing");
        doc.setErrorMsg(null);
        doc.setChunkCount(0);
        documentMapper.updateById(doc);

        // 重算知识库统计（此刻旧分块已删、doc.chunkCount 已清零，计数准确）
        recomputeKbCounts(kb);

        // 事务提交后异步处理（避免异步线程读到未提交的旧分块导致重复数据）
        triggerAsyncProcessing(doc.getId(), kb.getId());
        return toDocumentVO(doc);
    }

    /**
     * 清理文档的分块和向量数据（用于重试前的清理）
     */
    private void cleanupDocumentChunks(DocumentDO doc, KnowledgeBaseDO kb) {
        List<DocumentChunkDO> oldChunks = chunkMapper.selectList(
                new LambdaQueryWrapper<DocumentChunkDO>()
                        .eq(DocumentChunkDO::getDocumentId, doc.getId())
        );

        // 向量清理：best-effort，模型配置缺失时跳过向量删除，但始终清理 DB 记录
        try {
            ModelDO embeddingModel = documentProcessor.resolveEmbeddingModel(kb.getEmbeddingModelId());
            ModelProviderDO provider = documentProcessor.resolveProvider(embeddingModel);
            for (DocumentChunkDO chunk : oldChunks) {
                vectorStoreService.deleteWithProvider(chunk.getId(), provider, embeddingModel);
            }
        } catch (BusinessException e) {
            log.warn("清理分块时无法删除向量数据（模型配置缺失）: docId={}, reason={}", doc.getId(), e.getMessage());
        }

        chunkMapper.delete(
                new LambdaQueryWrapper<DocumentChunkDO>()
                        .eq(DocumentChunkDO::getDocumentId, doc.getId())
        );

        // 知识库计数不在此处手工回退：由 retryDocument 在重置 doc.chunkCount 后统一 recomputeKbCounts，
        // 避免"分块已删、doc.chunk_count 尚未清零"中间态导致的统计错误
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(String documentId) {
        DocumentDO doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new BusinessException(6003, "文档不存在");
        }

        // 先查询分块（用于从向量数据库删除）
        List<DocumentChunkDO> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<DocumentChunkDO>()
                        .eq(DocumentChunkDO::getDocumentId, documentId)
        );

        // 从向量数据库删除：best-effort，模型配置缺失时跳过向量删除，但始终清理 DB 记录
        KnowledgeBaseDO docKb = knowledgeBaseMapper.selectById(doc.getKnowledgeId());
        if (docKb != null) {
            try {
                ModelDO embeddingModel = documentProcessor.resolveEmbeddingModel(docKb.getEmbeddingModelId());
                ModelProviderDO provider = documentProcessor.resolveProvider(embeddingModel);
                for (DocumentChunkDO chunk : chunks) {
                    vectorStoreService.deleteWithProvider(chunk.getId(), provider, embeddingModel);
                }
            } catch (BusinessException e) {
                log.warn("删除文档时无法清理向量数据（模型配置缺失）: docId={}, reason={}", documentId, e.getMessage());
            }
        }

        // 删除分块记录
        chunkMapper.delete(
                new LambdaQueryWrapper<DocumentChunkDO>()
                        .eq(DocumentChunkDO::getDocumentId, documentId)
        );

        // 删除文档记录
        documentMapper.deleteById(documentId);

        // 重算知识库统计（文档记录已删除，从 DB 计数）
        if (docKb != null) {
            recomputeKbCounts(docKb);
        }
    }

    // ==================== 检索 ====================

    @Override
    public List<SearchResultVO> search(String knowledgeId, String query, int topK) {
        // 1. 向量检索（使用知识库关联的 embedding 模型 → provider）
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(knowledgeId);
        if (kb == null) {
            throw new BusinessException(6001, "知识库不存在");
        }
        ModelDO embeddingModel = documentProcessor.resolveEmbeddingModel(kb.getEmbeddingModelId());
        ModelProviderDO provider = documentProcessor.resolveProvider(embeddingModel);

        List<VectorSearchResult> results = vectorStoreService.searchWithProvider(query, topK, provider, embeddingModel, knowledgeId);

        // 2. 转换为 VO
        return results.stream().map(r -> {
            DocumentChunkDO chunk = chunkMapper.selectById(r.getChunkId());
            if (chunk == null) return null;

            DocumentDO doc = documentMapper.selectById(chunk.getDocumentId());

            SearchResultVO vo = new SearchResultVO();
            vo.setChunkId(chunk.getId());
            vo.setDocumentId(chunk.getDocumentId());
            vo.setDocumentName(doc != null ? doc.getName() : "Unknown");
            vo.setContent(chunk.getContent());
            vo.setScore(r.getScore());
            vo.setChunkIndex(chunk.getChunkIndex());
            return vo;
        }).filter(v -> v != null).collect(Collectors.toList());
    }

    // ==================== Agent 绑定 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentKnowledgeBindingVO bindKnowledge(BindKnowledgeDTO dto) {
        // S3: 跨租户防护——校验被绑定的知识库属于当前工作空间，
        // 否则工作空间 B 可把自身 Agent 绑定到 A 的私有知识库，造成 RAG 跨租户泄露。
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(dto.getKnowledgeId());
        if (kb == null) {
            throw new BusinessException(6001, "知识库不存在");
        }
        String wsId = RuntimeContext.getWorkspaceId();
        if (!wsId.equals(kb.getWorkspaceId())) {
            throw new BusinessException(4003, "无权绑定其他工作空间的知识库");
        }

        // 检查是否已绑定
        Long count = bindingMapper.selectCount(
                new LambdaQueryWrapper<AgentKnowledgeBindingDO>()
                        .eq(AgentKnowledgeBindingDO::getAgentId, dto.getAgentId())
                        .eq(AgentKnowledgeBindingDO::getKnowledgeId, dto.getKnowledgeId())
        );
        if (count > 0) {
            throw new BusinessException(6004, "该 Agent 已绑定此知识库");
        }

        AgentKnowledgeBindingDO binding = new AgentKnowledgeBindingDO();
        binding.setWorkspaceId(wsId);
        binding.setAgentId(dto.getAgentId());
        binding.setKnowledgeId(dto.getKnowledgeId());
        binding.setTopK(dto.getTopK() != null ? dto.getTopK() : 5);
        binding.setSimilarityThreshold(dto.getSimilarityThreshold() != null ? dto.getSimilarityThreshold() : 0.7);
        binding.setCreatedAt(LocalDateTime.now());

        bindingMapper.insert(binding);
        return toBindingVO(binding);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentKnowledgeBindingVO updateBinding(String bindingId, BindKnowledgeDTO dto) {
        AgentKnowledgeBindingDO binding = bindingMapper.selectById(bindingId);
        if (binding == null) {
            throw new BusinessException(6007, "绑定关系不存在");
        }
        if (dto.getTopK() != null) binding.setTopK(dto.getTopK());
        if (dto.getSimilarityThreshold() != null) binding.setSimilarityThreshold(dto.getSimilarityThreshold());
        bindingMapper.updateById(binding);
        return toBindingVO(binding);
    }

    @Override
    public void unbindKnowledge(String bindingId) {
        // S3: 绑定表已纳入 workspace 隔离，拦截器会自动追加 workspace 条件，
        // 跨租户解绑会因查不到记录而失败；此处再显式校验一次，返回明确错误。
        AgentKnowledgeBindingDO binding = bindingMapper.selectById(bindingId);
        if (binding == null) {
            throw new BusinessException(6007, "绑定关系不存在");
        }
        if (!RuntimeContext.getWorkspaceId().equals(binding.getWorkspaceId())) {
            throw new BusinessException(4003, "无权操作该绑定关系");
        }
        bindingMapper.deleteById(bindingId);
    }

    @Override
    public List<AgentKnowledgeBindingVO> listBindings(String agentId) {
        List<AgentKnowledgeBindingDO> bindings = bindingMapper.selectList(
                new LambdaQueryWrapper<AgentKnowledgeBindingDO>()
                        .eq(AgentKnowledgeBindingDO::getAgentId, agentId)
        );
        return bindings.stream().map(this::toBindingVO).collect(Collectors.toList());
    }

    @Override
    public void removeAllBindings(String agentId) {
        bindingMapper.delete(
                new LambdaQueryWrapper<AgentKnowledgeBindingDO>()
                        .eq(AgentKnowledgeBindingDO::getAgentId, agentId)
        );
    }

    @Override
    public List<AgentKnowledgeBindingVO> listBindingsByKnowledge(String knowledgeId) {
        // S3: 校验知识库归属，防止越权列举其他工作空间的绑定
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(knowledgeId);
        if (kb == null) {
            throw new BusinessException(6001, "知识库不存在");
        }
        if (!RuntimeContext.getWorkspaceId().equals(kb.getWorkspaceId())) {
            throw new BusinessException(4003, "无权访问该知识库的绑定");
        }
        List<AgentKnowledgeBindingDO> bindings = bindingMapper.selectList(
                new LambdaQueryWrapper<AgentKnowledgeBindingDO>()
                        .eq(AgentKnowledgeBindingDO::getKnowledgeId, knowledgeId)
        );
        return bindings.stream().map(this::toBindingVO).collect(Collectors.toList());
    }

    // ==================== 辅助方法 ====================

    private String getFileType(String filename) {
        if (filename == null) return "unknown";
        int lastDot = filename.lastIndexOf(".");
        if (lastDot < 0) return "unknown";
        return filename.substring(lastDot + 1).toLowerCase();
    }

    /**
     * 从数据库重算知识库的文档数与分块数（E10）。
     *
     * 此前用应用层 += / -= 维护 docCount/chunkCount，在异常中断、重试、并发等路径下易漂移。
     * 改为每次变更后从 DB 重算：docCount = 该库文档行数，chunkCount = 各文档 chunk_count 之和。
     * document 表在租户白名单（不自动加 workspace 过滤），按 knowledgeId 过滤即等价于该租户范围内。
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
     * 触发文档异步处理（分块 + 向量化）。
     * 若当前处于事务中（如 retryDocument），需等事务提交后再触发，
     * 否则异步线程可能读到未提交的旧分块，导致重复数据。
     */
    private void triggerAsyncProcessing(String documentId, String knowledgeId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    documentProcessor.processAsync(documentId, knowledgeId);
                }
            });
        } else {
            documentProcessor.processAsync(documentId, knowledgeId);
        }
    }

    /**
     * 上传阶段（同步解析）失败时，将文档标记为 error。
     */
    private void markUploadError(DocumentDO doc, String message) {
        doc.setStatus("error");
        doc.setErrorMsg(message != null
                ? message.substring(0, Math.min(message.length(), 2000))
                : "未知错误");
        documentMapper.updateById(doc);
    }

    private KnowledgeBaseVO toKnowledgeBaseVO(KnowledgeBaseDO kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        BeanUtils.copyProperties(kb, vo);
        return vo;
    }

    private DocumentVO toDocumentVO(DocumentDO doc) {
        DocumentVO vo = new DocumentVO();
        BeanUtils.copyProperties(doc, vo);
        return vo;
    }

    private AgentKnowledgeBindingVO toBindingVO(AgentKnowledgeBindingDO binding) {
        AgentKnowledgeBindingVO vo = new AgentKnowledgeBindingVO();
        BeanUtils.copyProperties(binding, vo);
        // 获取知识库名称
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(binding.getKnowledgeId());
        if (kb != null) {
            vo.setKnowledgeName(kb.getName());
        }
        return vo;
    }
}
