package com.agentone.auth.service;

import com.agentone.auth.dto.LoginDTO;
import com.agentone.auth.dto.RegisterDTO;
import com.agentone.auth.vo.AuthVO;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 注册
     */
    AuthVO register(RegisterDTO dto);

    /**
     * 登录
     */
    AuthVO login(LoginDTO dto);

    /**
     * 切换工作空间（重新签发 JWT）
     */
    AuthVO switchWorkspace(String userId, String targetWorkspaceId);

    /**
     * C2: 用 refresh token 重新签发 access token（及滚动 refresh token）
     */
    AuthVO refreshToken(String userId, String workspaceId, String email);
}
