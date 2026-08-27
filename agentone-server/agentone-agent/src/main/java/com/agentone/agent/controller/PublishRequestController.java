package com.agentone.agent.controller;

import com.agentone.agent.dto.RejectPublishRequestDTO;
import com.agentone.agent.dto.SubmitPublishRequestDTO;
import com.agentone.agent.service.PublishRequestService;
import com.agentone.agent.vo.PublishRequestVO;
import com.agentone.common.result.PageResult;
import com.agentone.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Agent 发布审批 Controller（课题⑩）
 * 发布唯一路径 = 审批（原 /api/agents/{id}/publish 直达端点已删除）。
 * 角色门禁：提交 = 写权限角色（Filter 保证）；审批 = admin/owner（Service 校验）；
 * auditor/developer 列表可见范围由 Service 按角色分流。
 */
@RestController
@RequestMapping("/api/publish-requests")
@RequiredArgsConstructor
public class PublishRequestController {

    private final PublishRequestService publishRequestService;

    /** 提交发布审批 */
    @PostMapping
    public Result<PublishRequestVO> submit(@Valid @RequestBody SubmitPublishRequestDTO dto) {
        return Result.ok(publishRequestService.submit(dto));
    }

    /** 审批列表：?status=&agentId= 过滤 */
    @GetMapping
    public Result<PageResult<PublishRequestVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.ok(publishRequestService.list(status, agentId, page, size));
    }

    /** 审批通过 */
    @PostMapping("/{id}/approve")
    public Result<PublishRequestVO> approve(@PathVariable String id) {
        return Result.ok(publishRequestService.approve(id));
    }

    /** 驳回（理由必填，8004） */
    @PostMapping("/{id}/reject")
    public Result<PublishRequestVO> reject(@PathVariable String id,
                                           @RequestBody(required = false) RejectPublishRequestDTO dto) {
        return Result.ok(publishRequestService.reject(id, dto));
    }

    /** 撤回（仅提交人本人） */
    @PostMapping("/{id}/withdraw")
    public Result<PublishRequestVO> withdraw(@PathVariable String id) {
        return Result.ok(publishRequestService.withdraw(id));
    }
}
