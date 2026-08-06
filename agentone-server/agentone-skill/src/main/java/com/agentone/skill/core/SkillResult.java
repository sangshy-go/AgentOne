package com.agentone.skill.core;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Skill 执行结果
 */
@Data
@Builder
public class SkillResult {

    /** 是否成功 */
    private boolean success;

    /** 返回数据 */
    private Map<String, Object> data;

    /** 错误信息（失败时） */
    private String errorMessage;

    /** 执行耗时（毫秒） */
    private Long durationMs;

    /** Token 消耗 */
    private Integer tokenCount;

    /**
     * 创建成功结果
     */
    public static SkillResult success(Map<String, Object> data, long durationMs) {
        return SkillResult.builder()
                .success(true)
                .data(data)
                .durationMs(durationMs)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static SkillResult failure(String errorMessage, long durationMs) {
        return SkillResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .durationMs(durationMs)
                .build();
    }
}
