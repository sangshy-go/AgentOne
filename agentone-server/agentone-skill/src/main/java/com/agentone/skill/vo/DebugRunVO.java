package com.agentone.skill.vo;

import lombok.Data;

import java.util.Map;

/**
 * 调试向导第 3 步：执行结果
 */
@Data
public class DebugRunVO {

    private boolean success;
    private Map<String, Object> data;
    private String errorMessage;
    private Long durationMs;
    /** 本次调试执行的追踪 ID（同步写入 skill_call_log.trace_id） */
    private String traceId;
    /** 审计关联的会话 ID */
    private String sessionId;
}
