package com.agentone.knowledge.controller;

import com.agentone.common.result.Result;
import com.agentone.knowledge.dto.BindKnowledgeDTO;
import com.agentone.knowledge.dto.KnowledgeBaseDTO;
import com.agentone.knowledge.service.KnowledgeService;
import com.agentone.knowledge.vo.AgentKnowledgeBindingVO;
import com.agentone.knowledge.vo.DocumentVO;
import com.agentone.knowledge.vo.KnowledgeBaseVO;
import com.agentone.knowledge.vo.SearchResultVO;
import com.agentone.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库管理接口
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    /**
     * P3: 分页与 topK 入参收敛。
     *
     * 此前 page / size / topK 直接透传，客户端可传 size=1000000 或 topK=100000，
     * 单次请求即拉取海量行/向量，造成 DB 与内存压力（可被用于放大攻击）。
     * 这里在入口统一 clamp：size ∈ [1,100]、page ∈ [1,10000]、topK ∈ [1,20]。
     */
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE = 10000;
    private static final int MAX_TOP_K = 20;

    private final KnowledgeService knowledgeService;

    private static int clampPage(Integer page) {
        if (page == null || page < 1) return 1;
        return Math.min(page, MAX_PAGE);
    }

    private static int clampSize(Integer size) {
        if (size == null || size < 1) return DEFAULT_PAGE_SIZE;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static int clampTopK(int topK) {
        return Math.max(1, Math.min(topK, MAX_TOP_K));
    }

    // ==================== 知识库管理 ====================

    @PostMapping("/bases")
    public Result<KnowledgeBaseVO> createKnowledgeBase(@RequestBody KnowledgeBaseDTO dto) {
        return Result.success(knowledgeService.createKnowledgeBase(dto));
    }

    @GetMapping("/bases")
    public Result<PageResult<KnowledgeBaseVO>> listKnowledgeBases(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(knowledgeService.listKnowledgeBases(clampPage(page), clampSize(size)));
    }

    @GetMapping("/bases/{id}")
    public Result<KnowledgeBaseVO> getKnowledgeBase(@PathVariable String id) {
        return Result.success(knowledgeService.getKnowledgeBase(id));
    }

    @PutMapping("/bases/{id}")
    public Result<KnowledgeBaseVO> updateKnowledgeBase(
            @PathVariable String id, @RequestBody KnowledgeBaseDTO dto) {
        return Result.success(knowledgeService.updateKnowledgeBase(id, dto));
    }

    @DeleteMapping("/bases/{id}")
    public Result<Void> deleteKnowledgeBase(@PathVariable String id) {
        knowledgeService.deleteKnowledgeBase(id);
        return Result.success();
    }

    // ==================== 文档管理 ====================

    @PostMapping("/bases/{knowledgeId}/documents")
    public Result<DocumentVO> uploadDocument(
            @PathVariable String knowledgeId,
            @RequestParam("file") MultipartFile file) {
        return Result.success(knowledgeService.uploadDocument(knowledgeId, file));
    }

    @GetMapping("/bases/{knowledgeId}/documents")
    public Result<PageResult<DocumentVO>> listDocuments(
            @PathVariable String knowledgeId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(knowledgeService.listDocuments(knowledgeId, clampPage(page), clampSize(size)));
    }

    @DeleteMapping("/documents/{documentId}")
    public Result<Void> deleteDocument(@PathVariable String documentId) {
        knowledgeService.deleteDocument(documentId);
        return Result.success();
    }

    @PostMapping("/documents/{documentId}/retry")
    public Result<DocumentVO> retryDocument(@PathVariable String documentId) {
        return Result.success(knowledgeService.retryDocument(documentId));
    }

    // ==================== 检索 ====================

    @PostMapping("/bases/{knowledgeId}/search")
    public Result<List<SearchResultVO>> search(
            @PathVariable String knowledgeId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(required = false) Double similarityThreshold) {
        return Result.success(knowledgeService.search(knowledgeId, query, clampTopK(topK), similarityThreshold));
    }

    // ==================== Agent 绑定 ====================

    @PostMapping("/bindings")
    public Result<AgentKnowledgeBindingVO> bindKnowledge(@RequestBody BindKnowledgeDTO dto) {
        return Result.success(knowledgeService.bindKnowledge(dto));
    }

    @DeleteMapping("/bindings/{bindingId}")
    public Result<Void> unbindKnowledge(@PathVariable String bindingId) {
        knowledgeService.unbindKnowledge(bindingId);
        return Result.success();
    }

    @PutMapping("/bindings/{bindingId}")
    public Result<AgentKnowledgeBindingVO> updateBinding(
            @PathVariable String bindingId, @RequestBody BindKnowledgeDTO dto) {
        return Result.success(knowledgeService.updateBinding(bindingId, dto));
    }

    @GetMapping("/agents/{agentId}/bindings")
    public Result<List<AgentKnowledgeBindingVO>> listBindings(@PathVariable String agentId) {
        return Result.success(knowledgeService.listBindings(agentId));
    }

    @GetMapping("/bases/{knowledgeId}/bindings")
    public Result<List<AgentKnowledgeBindingVO>> listBindingsByKnowledge(@PathVariable String knowledgeId) {
        return Result.success(knowledgeService.listBindingsByKnowledge(knowledgeId));
    }
}
