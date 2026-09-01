package com.agentone.skill.core;

import com.agentone.skill.entity.SkillCallLogDO;
import com.agentone.skill.mapper.SkillCallLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Skill 调用链审计记录器。
 * 每次 Skill 被 Agent 调用时落库 skill_call_log，供 Phase 2 审计看板使用。
 * 记录失败只告警不抛出——审计不能影响主对话链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillCallLogRecorder {

    private final SkillCallLogMapper callLogMapper;

    public void record(String workspaceId, String agentId, String sessionId,
                       String skillId, String traceId, String inputParams,
                       String outputResult, long durationMs,
                       boolean success, String errorMessage) {
        try {
            SkillCallLogDO logDO = new SkillCallLogDO();
            logDO.setWorkspaceId(workspaceId);
            logDO.setAgentId(agentId);
            logDO.setSessionId(sessionId);
            logDO.setSkillId(skillId);
            logDO.setTraceId(traceId);
            logDO.setInputParams(inputParams != null ? inputParams : "{}");
            logDO.setOutputResult(outputResult != null ? outputResult : "{}");
            logDO.setDurationMs(durationMs);
            logDO.setTokenCount(0);
            logDO.setStatus(success ? "success" : "failed");
            logDO.setErrorMessage(errorMessage);
            logDO.setCreatedAt(LocalDateTime.now());
            callLogMapper.insert(logDO);
        } catch (Exception e) {
            log.warn("Skill 调用日志落库失败: skillId={}, error={}", skillId, e.getMessage());
        }
    }
}
