package com.agentone.common.interceptor;

import com.agentone.common.context.RuntimeContext;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;

import java.util.Set;

/**
 * MyBatis-Plus 多租户拦截器
 * 自动为 SQL 追加 WHERE workspace_id = ? 条件
 */
public class WorkspaceInterceptor implements TenantLineHandler {

    /**
     * 不需要 workspace_id 隔离的表（白名单）
     * 包含：没有 workspace_id 列的表 + 系统表
     * 没有 workspace_id 的表：workspace, sys_user, user_workspace,
     *   document, document_chunk, chat_message, task_execution_log
     * 这些表通过父实体的 workspace_id 间接隔离
     *
     * model_provider, model：模型供应商和模型是跨 workspace 的共享配置资源，
     *   知识库绑定 provider_id/model_id 后需要加载配置（用于向量化/推理）；
     *   model 表无 workspace_id 列，跨租户风险由 ChatServiceImpl.buildReActAgent
     *   中的 provider.workspaceId 显式校验兜底（见 S4）。
     *
     * 注意：agent_knowledge_binding / agent_skill_binding 已从白名单移除（V14 迁移补充
     *   workspace_id 列），由本拦截器自动注入与过滤，防止跨租户 RAG 泄露与越权解绑。
     */
    private static final Set<String> IGNORE_TABLES = Set.of(
            "sys_user",
            "user_workspace",
            "workspace",
            "document",
            "document_chunk",
            "chat_message",
            "task_execution_log",
            "flyway_schema_history",
            "model_provider",
            "model"
    );

    @Override
    public Expression getTenantId() {
        String workspaceId = RuntimeContext.getWorkspaceId();
        if (workspaceId == null) {
            throw new IllegalStateException("workspace_id is null, cannot isolate data");
        }
        return new StringValue(workspaceId);
    }

    @Override
    public String getTenantIdColumn() {
        return "workspace_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return IGNORE_TABLES.contains(tableName);
    }
}
