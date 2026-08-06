package com.agentone.apikey.controller;

import com.agentone.apikey.dto.CreateApiKeyDTO;
import com.agentone.apikey.service.ApiKeyService;
import com.agentone.apikey.vo.ApiKeyVO;
import com.agentone.apikey.vo.CreateApiKeyVO;
import com.agentone.common.result.PageResult;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API Key 管理接口
 * 管理端 API，需 JWT 认证
 */
@RestController
@RequestMapping("/api/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    /**
     * 创建 API Key（仅创建时返回完整明文）
     */
    @PostMapping
    public Result<CreateApiKeyVO> create(@Valid @RequestBody CreateApiKeyDTO dto) {
        String workspaceId = RuntimeContext.getWorkspaceId();
        CreateApiKeyVO vo = apiKeyService.create(workspaceId, dto);
        return Result.ok(vo);
    }

    /**
     * 查询当前工作空间的 API Key 列表
     */
    @GetMapping
    public Result<PageResult<ApiKeyVO>> list(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        String workspaceId = RuntimeContext.getWorkspaceId();
        return Result.ok(apiKeyService.list(workspaceId, page, size));
    }

    /**
     * 停用 API Key
     */
    @DeleteMapping("/{id}")
    public Result<Void> disable(@PathVariable String id) {
        String workspaceId = RuntimeContext.getWorkspaceId();
        apiKeyService.disable(workspaceId, id);
        return Result.ok();
    }
}
