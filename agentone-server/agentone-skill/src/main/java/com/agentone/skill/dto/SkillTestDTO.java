package com.agentone.skill.dto;

import lombok.Data;

import java.util.Map;

/**
 * Skill 测试调用入参
 */
@Data
public class SkillTestDTO {

    /** 模拟 LLM 传入的调用参数 */
    private Map<String, Object> params;
}
