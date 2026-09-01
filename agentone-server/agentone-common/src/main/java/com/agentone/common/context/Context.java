package com.agentone.common.context;

import lombok.Data;

/**
 * 上下文数据对象
 */
@Data
public class Context {

    private String userId;
    private String workspaceId;
    private String email;
    private String agentId;
    /** 用户在当前工作空间的角色：owner/admin/developer/observer，由 WorkspaceRbacFilter 按请求填充 */
    private String role;

    public static Context of(String userId, String workspaceId) {
        Context ctx = new Context();
        ctx.setUserId(userId);
        ctx.setWorkspaceId(workspaceId);
        return ctx;
    }

    public static Context of(String userId, String workspaceId, String agentId) {
        Context ctx = of(userId, workspaceId);
        ctx.setAgentId(agentId);
        return ctx;
    }
}
