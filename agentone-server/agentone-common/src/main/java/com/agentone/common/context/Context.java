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
