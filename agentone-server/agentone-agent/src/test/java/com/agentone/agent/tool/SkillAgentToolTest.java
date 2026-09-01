package com.agentone.agent.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SkillAgentTool 工具名转换规则单测。
 * skill ID 会作为 function name 发给 LLM，必须是 [a-zA-Z0-9_] 字符集。
 */
class SkillAgentToolTest {

    @Test
    void sanitizeToolName_replacesIllegalChars() {
        assertEquals("builtin_http_request", SkillAgentTool.sanitizeToolName("builtin-http-request"));
        assertEquals("builtin_knowledge_search", SkillAgentTool.sanitizeToolName("builtin-knowledge-search"));
    }

    @Test
    void sanitizeToolName_keepsLegalChars() {
        assertEquals("my_api_skill_2", SkillAgentTool.sanitizeToolName("my_api_skill_2"));
    }

    @Test
    void sanitizeToolName_nullOrBlank_fallback() {
        assertEquals("unknown_skill", SkillAgentTool.sanitizeToolName(null));
        assertEquals("unknown_skill", SkillAgentTool.sanitizeToolName("   "));
    }
}
