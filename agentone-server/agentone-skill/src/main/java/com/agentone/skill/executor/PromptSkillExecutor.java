package com.agentone.skill.executor;

import com.agentone.common.context.Context;
import com.agentone.skill.core.SkillDescriptor;
import com.agentone.skill.core.SkillExecutor;
import com.agentone.skill.core.SkillInvocation;
import com.agentone.skill.core.SkillResult;
import com.agentone.skill.entity.SkillDO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 内容型（prompt）Skill 执行器。
 *
 * "写内容即发布"的 Skill 形态：config 只存 Markdown 指令（{content}），
 * 运行时采用渐进式披露（Anthropic Agent Skills 模式）——
 * LLM 侧只看到技能的 name + description（无参 function 工具），
 * 判断与当前任务相关时通过 function call 加载指令全文。
 *
 * 与 api 型的差异：不发起任何出站请求（无 SSRF 面、不依赖外部服务），
 * 指令内容是工作空间私有资产，可审计、可版本化。
 *
 * 非 Spring Bean：DB 中每个 prompt Skill 对应一个实例，
 * 由 UserSkillBootstrap（启动加载）与 SkillService（增删改时）动态注册/注销。
 */
@Slf4j
public class PromptSkillExecutor implements SkillExecutor {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /** 内容型 Skill 无参数：LLM 只决定"何时加载"，不组装参数 */
    private static final Map<String, Object> EMPTY_SCHEMA =
            Map.of("type", "object", "properties", Map.of());

    private final SkillDO skill;
    private final ObjectMapper objectMapper;

    public PromptSkillExecutor(SkillDO skill, ObjectMapper objectMapper) {
        this.skill = skill;
        this.objectMapper = objectMapper;
    }

    @Override
    public SkillResult execute(SkillInvocation invocation, Context context) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> config = objectMapper.readValue(
                    skill.getConfig() != null ? skill.getConfig() : "{}", MAP_TYPE);
            Object content = config.get("content");
            if (content == null || content.toString().isBlank()) {
                return SkillResult.failure("Skill 配置缺少 content", System.currentTimeMillis() - start);
            }
            Map<String, Object> data = new HashMap<>();
            data.put("name", skill.getName());
            data.put("content", content.toString());
            return SkillResult.success(data, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("内容型 Skill 执行失败: id={}, error={}", skill.getId(), e.getMessage());
            return SkillResult.failure("Skill 指令解析失败: " + e.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }

    @Override
    public SkillDescriptor getDescriptor() {
        return SkillDescriptor.builder()
                .id(skill.getId())
                .name(skill.getName())
                // description 是渐进式披露的关键：LLM 凭它判断何时加载指令全文
                .description(skill.getDescription())
                .type("prompt")
                .version(skill.getVersion())
                .source(skill.getSource())
                .category(skill.getCategory())
                // Registry 跨租户共享，描述符携带归属空间供 test/debug 做越权校验
                .workspaceId(skill.getWorkspaceId())
                .enabled("active".equals(skill.getStatus()))
                .actionType(UserSkillExecutors.isActionType(skill, objectMapper))
                .inputSchema(EMPTY_SCHEMA)
                .outputSchema(EMPTY_SCHEMA)
                .build();
    }
}
