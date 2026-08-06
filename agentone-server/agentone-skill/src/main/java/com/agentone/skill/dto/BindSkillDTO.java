package com.agentone.skill.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 绑定 Skill DTO
 */
@Data
public class BindSkillDTO {

    @NotBlank(message = "Agent ID 不能为空")
    private String agentId;

    @NotBlank(message = "Skill ID 不能为空")
    private String skillId;

    /** Skill 版本锁定（可选） */
    private String skillVersion;

    /** Agent 级别参数覆盖（JSON 字符串） */
    private String configOverride;
}
