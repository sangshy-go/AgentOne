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
     * 查询用户在工作空间中的角色
     * @return 角色字符串（owner/admin/member），不存在返回 null
     */
    String getUserRole(String userId, String workspaceId);

    /**
     * 查询用户的所有工作空间 ID
     */
    java.util.List<String> getUserWorkspaceIds(String userId);

    /**
     * 查询用户的默认工作空间 ID（第一个）
     */
    String getDefaultWorkspaceId(String userId);

    /**
     * 删除工作空间（软删除）并清理 user_workspace 关联。
     *
     * <p>默认工作空间由 {@link #getUserWorkspaceIds(String)} 的关联顺序派生，
     * 删除关联行后用户的默认工作空间会自动从剩余关联重算（无剩余则变为 null），
     * 因此不会出现“默认工作空间悬空指向已删除空间”的情况。</p>
     *
     * @param workspaceId 工作空间 ID（调用方需先完成权限校验）
     * @throws com.agentone.common.exception.BusinessException 工作空间不存在时（code=2001）
     */
    void deleteWorkspace(String workspaceId);
}
