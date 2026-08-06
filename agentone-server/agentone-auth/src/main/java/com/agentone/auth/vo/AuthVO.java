package com.agentone.auth.vo;

import lombok.Data;

/**
 * 认证响应 VO（登录/注册/切换工作空间后返回）
 */
@Data
public class AuthVO {

    private String token;
    private String refreshToken;
    private String userId;
    private String email;
    private String nickname;
    private String workspaceId;
    private String workspaceName;
}
