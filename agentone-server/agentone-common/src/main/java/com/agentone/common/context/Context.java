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

    public static Context of(String userId, String workspaceId) {
        Context ctx = new Context();
        ctx.setUserId(userId);
        ctx.setWorkspaceId(workspaceId);
        return ctx;
    }
}
