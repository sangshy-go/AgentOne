package com.agentone.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(0, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 认证相关 1xxx
    EMAIL_ALREADY_EXISTS(1001, "邮箱已注册"),
    EMAIL_OR_PASSWORD_ERROR(1002, "邮箱或密码错误"),
    ACCOUNT_LOCKED(1003, "账号已锁定，请稍后重试"),
    INVALID_TOKEN(1004, "Token 无效或已过期"),
    USER_NOT_FOUND(1005, "用户不存在"),

    // 工作空间相关 2xxx
    WORKSPACE_NOT_FOUND(2001, "工作空间不存在"),
    WORKSPACE_NO_PERMISSION(2002, "无权访问该工作空间"),

    // Agent 相关 3xxx
    AGENT_NOT_FOUND(3001, "Agent 不存在"),

    // 知识库相关 4xxx
    KNOWLEDGE_NOT_FOUND(4001, "知识库不存在");

    private final int code;
    private final String message;
}
