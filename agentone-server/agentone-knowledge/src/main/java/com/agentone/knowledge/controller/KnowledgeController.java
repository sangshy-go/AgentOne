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

    private final KnowledgeService knowledgeService;

    // ==================== 知识库管理 ====================

    @PostMapping("/bases")
    public Result<KnowledgeBaseVO> createKnowledgeBase(@RequestBody KnowledgeBaseDTO dto) {
        return Result.success(knowledgeService.createKnowledgeBase(dto));
    }

    @GetMapping("/bases")
    public Result<PageResult<KnowledgeBaseVO>> listKnowledgeBases(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(knowledgeService.listKnowledgeBases(page, size));
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
        return Result.success(knowledgeService.listDocuments(knowledgeId, page, size));
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
            @RequestParam(defaultValue = "5") int topK) {
        return Result.success(knowledgeService.search(knowledgeId, query, topK));
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
