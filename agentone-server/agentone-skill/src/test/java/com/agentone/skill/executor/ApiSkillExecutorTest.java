package com.agentone.skill.executor;

import com.agentone.common.context.Context;
import com.agentone.skill.core.SkillDescriptor;
import com.agentone.skill.core.SkillInvocation;
import com.agentone.skill.core.SkillResult;
import com.agentone.skill.entity.SkillDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * ApiSkillExecutor 单测。
 * 只覆盖不发起真实 HTTP 的路径（参数校验 / SSRF / Descriptor 构建），
 * HTTP 行为由集成环境验证。
 */
class ApiSkillExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient = mock(WebClient.class);

    private SkillDO apiSkill(String config) {
        SkillDO skill = new SkillDO();
        skill.setId("skill-test-001");
        skill.setWorkspaceId("ws-1");
        skill.setName("天气查询");
        skill.setType("api");
        skill.setSource("custom");
        skill.setDescription("查询天气");
        skill.setInputSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}");
        skill.setOutputSchema("{}");
        skill.setConfig(config);
        skill.setVersion("1.0.0");
        skill.setStatus("active");
        return skill;
    }

    @Test
    void getDescriptor_buildsFromSkillDO() {
        ApiSkillExecutor executor = new ApiSkillExecutor(
                apiSkill("{\"url\":\"https://9.9.9.9/weather\",\"method\":\"GET\"}"),
                webClient, objectMapper);

        SkillDescriptor descriptor = executor.getDescriptor();
        assertEquals("skill-test-001", descriptor.getId());
        assertEquals("天气查询", descriptor.getName());
        assertEquals("api", descriptor.getType());
        assertTrue(descriptor.isEnabled());
        assertNotNull(descriptor.getInputSchema());
        assertEquals("object", descriptor.getInputSchema().get("type"));
    }

    @Test
    void getDescriptor_invalidSchemaJson_fallsBackToEmptySchema() {
        SkillDO skill = apiSkill("{\"url\":\"https://9.9.9.9/x\"}");
        skill.setInputSchema("这不是 JSON");
        ApiSkillExecutor executor = new ApiSkillExecutor(skill, webClient, objectMapper);

        SkillDescriptor descriptor = executor.getDescriptor();
        assertEquals("object", descriptor.getInputSchema().get("type"), "非法 Schema 应降级为空 object Schema");
    }

    @Test
    void getDescriptor_disabledStatus_notEnabled() {
        SkillDO skill = apiSkill("{\"url\":\"https://9.9.9.9/x\"}");
        skill.setStatus("disabled");
        ApiSkillExecutor executor = new ApiSkillExecutor(skill, webClient, objectMapper);

        assertFalse(executor.getDescriptor().isEnabled());
    }

    @Test
    void execute_configMissingUrl_returnsFailure() {
        ApiSkillExecutor executor = new ApiSkillExecutor(
                apiSkill("{\"method\":\"GET\"}"), webClient, objectMapper);

        SkillResult result = executor.execute(
                SkillInvocation.builder().skillId("skill-test-001").params(Map.of()).build(),
                Context.of("user-1", "ws-1"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("url"));
    }

    @Test
    void execute_ssrfUrl_returnsFailureWithoutHttp() {
        ApiSkillExecutor executor = new ApiSkillExecutor(
                apiSkill("{\"url\":\"http://127.0.0.1/admin\",\"method\":\"GET\"}"),
                webClient, objectMapper);

        SkillResult result = executor.execute(
                SkillInvocation.builder().skillId("skill-test-001").params(Map.of()).build(),
                Context.of("user-1", "ws-1"));

        assertFalse(result.isSuccess(), "内网地址必须被执行期 SSRF 校验拦截");
    }

    @Test
    void execute_invalidConfigJson_returnsFailure() {
        ApiSkillExecutor executor = new ApiSkillExecutor(
                apiSkill("坏掉的 JSON"), webClient, objectMapper);

        SkillResult result = executor.execute(
                SkillInvocation.builder().skillId("skill-test-001").params(Map.of()).build(),
                Context.of("user-1", "ws-1"));

        assertFalse(result.isSuccess());
    }
}
