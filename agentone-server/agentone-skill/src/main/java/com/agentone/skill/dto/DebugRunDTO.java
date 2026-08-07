package com.agentone.skill.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 调试向导第 3 步：执行入参。
 * 执行上下文（userId/workspaceId）强制取自当前登录态，不允许跨租户注入；
 * sessionId 可选，用于把调试执行与某个会话关联（留空则生成 debug- 前缀 ID）。
 */
@Data
public class DebugRunDTO {

    @NotBlank(message = "skillId 不能为空")
    private String skillId;

    /** 调用参数 */
    private Map<String, Object> params;

    /** 可选：关联的会话 ID（上下文注入） */
    private String sessionId;
}
