package com.agentone.common.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 变更审计日志（课题⑩）
 * 表 audit_log 由 V4 建立，本实体 + AuditFilter 完成接线激活。
 * 带 workspace_id 列，自动受租户拦截器隔离（不进 IGNORE_TABLES）。
 */
@Data
@TableName("audit_log")
public class AuditLogDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workspaceId;

    private String operatorId;

    /** 动作：路径推导的语义动作（如 approve / create / update / delete） */
    private String action;

    /** 资源类型：/api/ 后第一段（如 agents / publish-requests） */
    private String resourceType;

    /** 资源 ID：路径中的 UUID 段（无则空） */
    private String resourceId;

    /** JSONB - {method, path, operatorEmail}，不记录请求体（防密钥泄漏） */
    private String detail;

    private LocalDateTime createdAt;
}
