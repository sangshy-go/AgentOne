package com.agentone.api.controller;

import com.agentone.api.service.AuditLogService;
import com.agentone.api.vo.AuditLogVO;
import com.agentone.common.result.PageResult;
import com.agentone.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志查询（课题⑩）
 * 角色门禁在 Service 层：仅 owner/admin/auditor 可查
 */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public Result<PageResult<AuditLogVO>> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.ok(auditLogService.list(action, resourceType, keyword, page, size));
    }
}
