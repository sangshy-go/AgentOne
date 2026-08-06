package com.agentone.agent.tool;

import com.agentone.common.context.Context;
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
 * 工具名规则：skill ID 中的非字母数字字符替换为下划线，
 * 例如 "builtin-http-request" → "builtin_http_request"。
 */
@Slf4j
public class SkillAgentTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SkillExecutor executor;
    private final SkillDescriptor descriptor;
    private final String toolName;

    public SkillAgentTool(SkillExecutor executor) {
        this.executor = executor;
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

    /**
     * 执行 Skill。
     *
     * 流程：
     * 1. 从 ToolCallParam.getInput() 取 LLM 传入的参数 Map
     * 2. 从 ToolCallParam.getRuntimeContext() 还原用户/工作空间上下文
     * 3. 调用 SkillExecutor.execute()（同步，在 boundedElastic 线程上运行避免阻塞 Reactor 事件循环）
     * 4. 将 SkillResult 转换为 ToolResultBlock
     */
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

            // 从 AgentScope RuntimeContext 还原业务上下文
            Context context = buildContext(param);

            log.info("执行 Skill: name={}, toolName={}, traceId={}",
                    descriptor.getName(), toolName, invocation.getTraceId());

            SkillResult result = executor.execute(invocation, context);

            long duration = System.currentTimeMillis() - start;
            log.info("Skill 执行完成: name={}, success={}, durationMs={}",
                    descriptor.getName(), result.isSuccess(), duration);

            if (result.isSuccess()) {
                return ToolResultBlock.text(formatData(result.getData()));
            } else {
                return ToolResultBlock.error(
                        result.getErrorMessage() != null ? result.getErrorMessage() : "Skill 执行失败");
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 从 AgentScope RuntimeContext 中提取 userId / workspaceId，
     * 构建业务侧 Context 供 SkillExecutor 使用。
     */
    private Context buildContext(ToolCallParam param) {
        String userId = null;
        String workspaceId = null;
        if (param.getRuntimeContext() != null) {
            userId = param.getRuntimeContext().getUserId();
            Object ws = param.getRuntimeContext().get("workspace_id");
            workspaceId = ws != null ? ws.toString() : null;
        }
        return Context.of(userId, workspaceId);
    }

    /** 将 SkillResult.data Map 序列化为 JSON 字符串，作为工具输出返回给 LLM。 */
    private String formatData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "执行成功";
        }
        try {
            return MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            return String.valueOf(data);
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
