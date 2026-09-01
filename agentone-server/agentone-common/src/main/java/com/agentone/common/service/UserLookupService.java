package com.agentone.common.service;

import java.util.List;

/**
 * 用户信息查询接口（跨模块）。
 * sys_user 归属 auth 模块，其他模块（如 workspace 的成员管理）通过本接口查询，
 * 避免反向依赖。接口在 common，实现在 auth（与 WorkspaceService 同模式）。
 */
public interface UserLookupService {

    /**
     * 按邮箱精确查找已注册用户，不存在返回 null
     */
    UserInfo findByEmail(String email);

    /**
     * 批量按 ID 查找（成员列表富化用，避免循环单查），空入参返回空列表
     */
    List<UserInfo> findByIds(List<String> userIds);

    record UserInfo(String userId, String email, String nickname) {
    }
}
