package com.agentone.skill.executor;

import com.agentone.common.context.Context;
import com.agentone.skill.core.SkillDescriptor;
import com.agentone.skill.core.SkillInvocation;
import com.agentone.skill.core.SkillResult;
import com.agentone.skill.entity.SkillDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PromptSkillExecutor 单测：内容型 Skill 的执行与描述符构建。
 * 无出站请求，全部路径可离线验证。
 */
class PromptSkillExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SkillDO promptSkill(String config) {
        SkillDO skill = new SkillDO();
        skill.setId("skill-prompt-001");
        skill.setWorkspaceId("ws-1");
        skill.setName("信贷审批报告规范");
        skill.setType("prompt");
        skill.setSource("custom");
        skill.setDescription("撰写信贷审批报告时加载的格式与合规要求");
        skill.setInputSchema("{}");
        skill.setOutputSchema("{}");
        skill.setConfig(config);
        skill.setVersion("1.0.0");
        skill.setStatus("active");
        return skill;
    }

    private SkillInvocation invocation() {
        return SkillInvocation.builder()
                .skillId("skill-prompt-001")
                .params(Map.of())
                .traceId("test-trace")
                .build();
    }

    @Test
    void execute_returnsInstructionContent() {
        PromptSkillExecutor executor = new PromptSkillExecutor(
                promptSkill("{\"content\":\"## 报告规范\\n1. 先给结论\"}"), objectMapper);

        SkillResult result = executor.execute(invocation(), Context.of("u1", "ws-1"));

        assertTrue(result.isSuccess());
        assertEquals("信贷审批报告规范", result.getData().get("name"));
        assertEquals("## 报告规范\n1. 先给结论", result.getData().get("content"));
        assertNotNull(result.getDurationMs());
    }

    @Test
    void execute_missingContent_fails() {
        PromptSkillExecutor executor = new PromptSkillExecutor(promptSkill("{}"), objectMapper);

        SkillResult result = executor.execute(invocation(), Context.of("u1", "ws-1"));

        assertFalse(result.isSuccess());
        assertEquals("Skill 配置缺少 content", result.getErrorMessage());
    }

    @Test
    void execute_blankContent_fails() {
        PromptSkillExecutor executor = new PromptSkillExecutor(
                promptSkill("{\"content\":\"   \"}"), objectMapper);

        assertFalse(executor.execute(invocation(), Context.of("u1", "ws-1")).isSuccess());
    }

    @Test
    void execute_invalidConfigJson_fails() {
        PromptSkillExecutor executor = new PromptSkillExecutor(
                promptSkill("不是 JSON"), objectMapper);

        SkillResult result = executor.execute(invocation(), Context.of("u1", "ws-1"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().startsWith("Skill 指令解析失败"));
    }

    @Test
    void execute_nullConfig_fails() {
        PromptSkillExecutor executor = new PromptSkillExecutor(promptSkill(null), objectMapper);

        assertFalse(executor.execute(invocation(), Context.of("u1", "ws-1")).isSuccess(),
                "config 为 null 应降级为空对象 → 缺 content 失败，而不是抛异常");
    }

    @Test
    void getDescriptor_buildsFromSkillDO() {
        PromptSkillExecutor executor = new PromptSkillExecutor(
                promptSkill("{\"content\":\"x\"}"), objectMapper);

        SkillDescriptor descriptor = executor.getDescriptor();
        assertEquals("skill-prompt-001", descriptor.getId());
        assertEquals("prompt", descriptor.getType());
        assertEquals("ws-1", descriptor.getWorkspaceId(), "描述符必须携带归属空间供越权校验");
        assertTrue(descriptor.isEnabled());
        assertEquals("object", descriptor.getInputSchema().get("type"));
        assertTrue(((Map<?, ?>) descriptor.getInputSchema().get("properties")).isEmpty(),
                "内容型 Skill 无参数，inputSchema 必须是空 object schema");
    }

    @Test
    void getDescriptor_disabledStatus_notEnabled() {
        SkillDO skill = promptSkill("{\"content\":\"x\"}");
        skill.setStatus("disabled");
        PromptSkillExecutor executor = new PromptSkillExecutor(skill, objectMapper);

        assertFalse(executor.getDescriptor().isEnabled());
    }
}
