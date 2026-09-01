package com.agentone.api.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志展示对象（课题⑩）
 * operatorEmail/method/path 由写入时的 detail JSON 解出，查询免 join
 */
@Data
public class AuditLogVO {

    private String id;
    private String operatorId;
    private String operatorEmail;
    private String action;
    private String resourceType;
    private String resourceId;
    private String method;
    private String path;
    private LocalDateTime createdAt;
}
