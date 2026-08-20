package com.agentone.skill.dto;

import lombok.Data;

import java.util.Map;

/**
 * 技能调用请求（广场「试一试」/ 动作型确认执行）
 */
@Data
public class InvokeSkillDTO {

    /** 调用参数 */
    private Map<String, Object> params;

    /**
     * 动作型技能二次调用携带的确认令牌。
     * 首次调用不传：返回草稿 + token；带有效 token 才真实执行。
     */
    private String confirmToken;
}
