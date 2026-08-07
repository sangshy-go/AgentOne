package com.agentone.agent.tool;

import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.skill.core.SkillCallLogRecorder;
import com.agentone.skill.core.SkillDescriptor;
import com.agentone.skill.core.SkillExecutor;
import com.agentone.skill.core.SkillInvocation;
import com.agentone.skill.core.SkillResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.UUID;

/**
 * 把 SkillExecutor 适配为 AgentScope AgentTool。
 *
 * ReActAgent 通过 Toolkit 调用此工具，LLM 发起 function call 后，
 * callAsync() 被触发，进而调用底层 SkillExecutor.execute()。
 *
 * 职责（Phase 2 工具化）：
 * 1. 在执行线程恢复业务 RuntimeContext（租户拦截器依赖 ThreadLocal，
 *    Reactor 线程默认无上下文，不恢复则 Skill 内的 DB 查询直接抛异常）
 * 2. 执行 Skill 并将调用链写入 skill_call_log（审计）
 *
 * 工具名规则：skill ID 中的非字母数字字符替换为下划线，
 * 例如 "builtin-http-request" → "builtin_http_request"。
 */
@Slf4j
public class SkillAgentTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SkillExecutor executor;
    private final SkillDescriptor descriptor;
    private final SkillCallLogRecorder recorder;
    private final String toolName;

    public SkillAgentTool(SkillExecutor executor, SkillCallLogRecorder recorder) {
        this.executor = executor;
        this.recorder = recorder;
        this.descriptor = executor.getDescriptor();
        this.toolName = sanitizeToolName(descriptor.getId());
    }

    @Override
    public String getName() {
        return toolName;
    }

    @Override
    public String getDescription() {
        return descriptor.getDescription() != null ? descriptor.getDescription() : descriptor.getName();
    }

    /**
     * 返回 JSON Schema 格式的参数定义，直接来自 SkillDescriptor.inputSchema。
     * AgentScope 将其序列化后发给 LLM 作为 function calling 的 parameters 字段。
     */
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = descriptor.getInputSchema();
        return schema != null ? schema : Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> {
            long start = System.currentTimeMillis();

            Map<String, Object> input = param.getInput() != null ? param.getInput() : Map.of();

            SkillInvocation invocation = SkillInvocation.builder()
                    .skillId(descriptor.getId())
                    .params(input)
                    .traceId(UUID.randomUUID().toString().substring(0, 8))
                    .build();

            // 从 AgentScope RuntimeContext 还原业务上下文（含 agentId）
            Context context = buildContext(param);

            log.info("执行 Skill: name={}, toolName={}, traceId={}",
                    descriptor.getName(), toolName, invocation.getTraceId());

            // 恢复 ThreadLocal 上下文：Skill 内部的租户表查询 / 审计落库都依赖它
            Context prev = RuntimeContext.get();
            SkillResult result;
            try {
                RuntimeContext.set(context);
                result = executor.execute(invocation, context);
            } finally {
                if (prev != null) {
                    RuntimeContext.set(prev);
                } else {
                    RuntimeContext.clear();
                }
            }

            long duration = System.currentTimeMillis() - start;
            log.info("Skill 执行完成: name={}, success={}, durationMs={}",
                    descriptor.getName(), result.isSuccess(), duration);

            recordCallLog(param, context, invocation, input, result, duration);

            if (result.isSuccess()) {
                return ToolResultBlock.text(formatData(result.getData()));
            } else {
                return ToolResultBlock.error(
                        result.getErrorMessage() != null ? result.getErrorMessage() : "Skill 执行失败");
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 调用链审计：落库 skill_call_log，失败只告警（Recorder 内部兜底） */
    private void recordCallLog(ToolCallParam param, Context context, SkillInvocation invocation,
                               Map<String, Object> input, SkillResult result, long duration) {
        if (recorder == null) {
            return;
        }
        String sessionId = null;
        if (param.getRuntimeContext() != null) {
            sessionId = param.getRuntimeContext().getSessionId();
        }
        recorder.record(
                context.getWorkspaceId(),
                context.getAgentId(),
                sessionId,
                descriptor.getId(),
                invocation.getTraceId(),
                toJson(input),
                result.isSuccess() ? formatData(result.getData()) : "{}",
                duration,
                result.isSuccess(),
                result.isSuccess() ? null : result.getErrorMessage());
    }

    /**
     * 从 AgentScope RuntimeContext 中提取 userId / workspaceId / agentId，
     * 构建业务侧 Context 供 SkillExecutor 使用。
     */
    private Context buildContext(ToolCallParam param) {
        String userId = null;
        String workspaceId = null;
        String agentId = null;
        if (param.getRuntimeContext() != null) {
            userId = param.getRuntimeContext().getUserId();
            Object ws = param.getRuntimeContext().get("workspace_id");
            workspaceId = ws != null ? ws.toString() : null;
            Object agent = param.getRuntimeContext().get("agent_id");
            agentId = agent != null ? agent.toString() : null;
        }
        return Context.of(userId, workspaceId, agentId);
    }

    /** 将 SkillResult.data Map 序列化为 JSON 字符串，作为工具输出返回给 LLM。 */
    private String formatData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "执行成功";
        }
        return toJson(data);
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /** 把 skill ID 转换为合法的 function name（只保留字母、数字、下划线）。 */
    static String sanitizeToolName(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return "unknown_skill";
        }
        return skillId.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
