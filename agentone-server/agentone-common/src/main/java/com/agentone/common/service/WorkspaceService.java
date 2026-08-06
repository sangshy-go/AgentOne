package com.agentone.common.service;

/**
 * 工作空间服务接口
 * 定义在 common 模块，由 workspace 模块实现
 */
public interface WorkspaceService {

    /**
     * 创建默认工作空间
     *
     * @param userId 用户 ID
     * @param email  邮箱（用于生成默认名称）
     * @return 工作空间 ID
     */
    String createDefaultWorkspace(String userId, String email);

    /**
     * 根据 ID 查询工作空间名称
     */
    String getWorkspaceName(String workspaceId);

    /**
     * 校验用户是否属于该工作空间
     */
    boolean checkPermission(String userId, String workspaceId);

    /**
     * 查询用户的所有工作空间 ID
     */
    java.util.List<String> getUserWorkspaceIds(String userId);

    /**
     * 查询用户的默认工作空间 ID（第一个）
     */
    String getDefaultWorkspaceId(String userId);
}
