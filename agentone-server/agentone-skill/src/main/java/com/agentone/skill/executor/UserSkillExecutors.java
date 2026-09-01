package com.agentone.skill.executor;

import com.agentone.skill.core.SkillExecutor;
import com.agentone.skill.entity.SkillDO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * 用户创建型（落 skill 表）Skill 的执行器工厂。
 *
 * type=api → ApiSkillExecutor（HTTP API 封装，面向 IT/集成侧）；
 * type=prompt → PromptSkillExecutor（内容型指令，面向全员创建）。
 * SkillService（CRUD）、UserSkillBootstrap（启动加载）共用此入口，
 * 保证两条路径构建执行器的行为一致。
 */
public final class UserSkillExecutors {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private UserSkillExecutors() {
    }

    /** 用户可创建/修改/删除的 Skill 类型（落 skill 表，区别于 builtin/mcp 虚拟挂载） */
    public static boolean isUserType(String type) {
        return "api".equals(type) || "prompt".equals(type);
    }

    public static SkillExecutor create(SkillDO skill, WebClient webClient, ObjectMapper objectMapper) {
        if ("prompt".equals(skill.getType())) {
            return new PromptSkillExecutor(skill, objectMapper);
        }
        return new ApiSkillExecutor(skill, webClient, objectMapper);
    }

    /**
     * 动作型判断（Skill 中心 v2）：config JSONB 中 actionType=true 的技能视为动作型，
     * 执行需「草稿→确认→执行」两阶段。config 非法或缺省一律 false（内容型默认无副作用）。
     */
    public static boolean isActionType(SkillDO skill, ObjectMapper objectMapper) {
        if (skill == null || skill.getConfig() == null || skill.getConfig().isBlank()) {
            return false;
        }
        try {
            Map<String, Object> config = objectMapper.readValue(skill.getConfig(), MAP_TYPE);
            return Boolean.TRUE.equals(config.get("actionType"));
        } catch (Exception e) {
            return false;
        }
    }
}
