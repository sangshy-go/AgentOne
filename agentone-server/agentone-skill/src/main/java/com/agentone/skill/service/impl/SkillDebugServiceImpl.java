package com.agentone.skill.service.impl;

import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.skill.core.JsonSchemaLiteValidator;
import com.agentone.skill.core.SkillCallLogRecorder;
import com.agentone.skill.core.SkillDescriptor;
import com.agentone.skill.core.SkillExecutor;
import com.agentone.skill.core.SkillInvocation;
import com.agentone.skill.core.SkillRegistry;
import com.agentone.skill.core.SkillResult;
import com.agentone.skill.dto.DebugPreviewDTO;
import com.agentone.skill.dto.DebugRunDTO;
import com.agentone.skill.entity.SkillDO;
import com.agentone.skill.mapper.SkillMapper;
import com.agentone.skill.service.SkillDebugService;
import com.agentone.skill.vo.DebugPreviewVO;
import com.agentone.skill.vo.DebugRunVO;
import com.agentone.skill.vo.DebugTargetVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Skill 调试器实现。
 *
 * 安全边界：
 * - 执行上下文 userId/workspaceId 强制取当前登录态，DebugRunDTO 只允许注入 sessionId，
 *   杜绝借调试器跨租户执行；
 * - 调试执行同样过 executor 内部的安全检查（如 ApiSkillExecutor 的执行期 SSRF 二次校验）；
 * - 调试执行写 skill_call_log（agentId 标记为 "debugger"），与对话调用共用审计表但可区分来源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillDebugServiceImpl implements SkillDebugService {

    private static final String DEBUGGER_AGENT_ID = "debugger";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final SkillRegistry skillRegistry;
    private final SkillMapper skillMapper;
    private final SkillCallLogRecorder callLogRecorder;
    private final ObjectMapper objectMapper;

    @Override
    public List<DebugTargetVO> listTargets() {
        // Registry 中即当前工作空间"可执行"的全集：builtin 全局 + 本空间 api + 本空间已连接 MCP 工具。
        // Registry 跨租户共享（api/mcp 描述符带归属 workspaceId），按空间过滤防泄露。
        String wsId = RuntimeContext.getWorkspaceId();
        return skillRegistry.listDescriptors().stream()
                .filter(d -> d.getWorkspaceId() == null || wsId.equals(d.getWorkspaceId()))
                .sorted(Comparator.comparing(SkillDescriptor::getType)
                        .thenComparing(SkillDescriptor::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toTargetVO)
                .toList();
    }

    @Override
    public DebugPreviewVO preview(DebugPreviewDTO dto) {
        SkillExecutor executor = requireExecutor(dto.getSkillId());
        SkillDescriptor descriptor = executor.getDescriptor();

        List<String> errors = JsonSchemaLiteValidator.validate(descriptor.getInputSchema(), dto.getParams());

        DebugPreviewVO vo = new DebugPreviewVO();
        vo.setValid(errors.isEmpty());
        vo.setErrors(errors);
        vo.setPlan(buildPlan(descriptor));
        return vo;
    }

    @Override
    public DebugRunVO run(DebugRunDTO dto) {
        SkillExecutor executor = requireExecutor(dto.getSkillId());

        String traceId = "debug-" + UUID.randomUUID().toString().substring(0, 8);
        String sessionId = dto.getSessionId() != null && !dto.getSessionId().isBlank()
                ? dto.getSessionId() : traceId;
        Map<String, Object> params = dto.getParams() != null ? dto.getParams() : Map.of();

        // 上下文强制取当前登录态（不允许跨租户注入）
        Context context = Context.of(RuntimeContext.getUserId(), RuntimeContext.getWorkspaceId());
        SkillInvocation invocation = SkillInvocation.builder()
                .skillId(dto.getSkillId())
                .params(params)
                .traceId(traceId)
                .build();

        long start = System.currentTimeMillis();
        SkillResult result;
        try {
            result = executor.execute(invocation, context);
        } catch (Exception e) {
            log.warn("调试执行异常: skillId={}, error={}", dto.getSkillId(), e.getMessage());
            result = SkillResult.failure("执行异常: " + e.getMessage(), System.currentTimeMillis() - start);
        }
        long duration = result.getDurationMs() != null ? result.getDurationMs() : System.currentTimeMillis() - start;

        // 审计：agentId 固定 "debugger" 标记来源，best-effort
        callLogRecorder.record(context.getWorkspaceId(), DEBUGGER_AGENT_ID, sessionId,
                dto.getSkillId(), traceId,
                writeJson(params), writeJson(result.getData()),
                duration, result.isSuccess(), result.getErrorMessage());

        DebugRunVO vo = new DebugRunVO();
        vo.setSuccess(result.isSuccess());
        vo.setData(result.getData());
        vo.setErrorMessage(result.getErrorMessage());
        vo.setDurationMs(duration);
        vo.setTraceId(traceId);
        vo.setSessionId(sessionId);
        return vo;
    }

    private SkillExecutor requireExecutor(String skillId) {
        SkillExecutor executor = skillRegistry.getExecutor(skillId)
                .orElseThrow(() -> new BusinessException(5002, "Skill 不存在或不可调试"));
        // S3: Registry 跨租户共享——描述符标注归属空间的 Skill 需校验越权
        SkillDescriptor descriptor = executor.getDescriptor();
        if (descriptor.getWorkspaceId() != null
                && !descriptor.getWorkspaceId().equals(RuntimeContext.getWorkspaceId())) {
            throw new BusinessException(5004, "无权调试其他工作空间的 Skill");
        }
        return executor;
    }

    /** 生成执行计划预览文本（不落真实请求） */
    private String buildPlan(SkillDescriptor descriptor) {
        if ("api".equals(descriptor.getType())) {
            // api Skill 从 DB 读 config 展示目标地址（selectById 自带租户过滤）
            SkillDO skill = skillMapper.selectById(descriptor.getId());
            if (skill != null && skill.getConfig() != null) {
                try {
                    Map<String, Object> config = objectMapper.readValue(skill.getConfig(), MAP_TYPE);
                    String method = String.valueOf(config.getOrDefault("method", "GET")).toUpperCase();
                    return "HTTP " + method + " " + config.get("url")
                            + "（GET/DELETE 参数走 query，POST/PUT 参数走 JSON body）";
                } catch (Exception ignored) {
                    // 坏 config 降级为通用描述
                }
            }
            return "HTTP 调用（config 解析失败，执行时将被 5007 拦截）";
        }
        if ("mcp".equals(descriptor.getType())) {
            return "MCP 工具调用（来源：" + descriptor.getSource() + "），参数按 inputSchema 组装";
        }
        return "内置执行器直接执行：" + descriptor.getName();
    }

    private DebugTargetVO toTargetVO(SkillDescriptor d) {
        DebugTargetVO vo = new DebugTargetVO();
        vo.setId(d.getId());
        vo.setName(d.getName());
        vo.setType(d.getType());
        vo.setDescription(d.getDescription());
        vo.setInputSchema(writeJson(d.getInputSchema()));
        return vo;
    }

    private String writeJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
