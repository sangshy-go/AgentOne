package com.agentone.skill.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 调试向导第 2 步：参数预检入参
 */
@Data
public class DebugPreviewDTO {

    @NotBlank(message = "skillId 不能为空")
    private String skillId;

    /** 调用参数 */
    private Map<String, Object> params;
}
