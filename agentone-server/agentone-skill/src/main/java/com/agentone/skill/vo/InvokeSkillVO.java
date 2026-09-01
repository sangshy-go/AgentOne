package com.agentone.skill.vo;

import lombok.Data;

import java.util.Map;

/**
 * 技能调用响应。
 *
 * 动作型技能两阶段：
 * - 首次调用（无 confirmToken）：confirmRequired=true + confirmToken + 草稿（params 回显），不执行；
 * - 二次调用（带有效 token）：真实执行，confirmRequired=false，result 字段有值。
 * 非动作型技能：直接执行，confirmRequired=false。
 */
@Data
public class InvokeSkillVO {

    private String skillId;
    private String skillName;

    /** 是否需要用户确认（动作型技能首次调用） */
    private boolean confirmRequired;
    /** 确认令牌（confirmRequired=true 时返回，5 分钟内有效） */
    private String confirmToken;
    /** 草稿：即将执行的参数回显 */
    private Map<String, Object> draftParams;

    /** 执行结果（真实执行后才有值） */
    private Boolean success;
    private Map<String, Object> data;
    private String errorMessage;
    private Long durationMs;
    private String traceId;
}
