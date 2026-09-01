package com.agentone.common.context;

/**
 * 运行时上下文（ThreadLocal）
 * 存储当前请求的用户信息和工作空间信息
 */
public final class RuntimeContext {

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    private RuntimeContext() {}

    public static void set(Context context) {
        HOLDER.set(context);
    }

    public static Context get() {
        return HOLDER.get();
    }

    public static String getUserId() {
        Context ctx = HOLDER.get();
        return ctx != null ? ctx.getUserId() : null;
    }

    public static String getWorkspaceId() {
        Context ctx = HOLDER.get();
        return ctx != null ? ctx.getWorkspaceId() : null;
    }

    public static String getRole() {
        Context ctx = HOLDER.get();
        return ctx != null ? ctx.getRole() : null;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
