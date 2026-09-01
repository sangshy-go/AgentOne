package com.agentone.skill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Skill 调用日志（调用链审计）
 * 对应 V4 迁移预留的 skill_call_log 表，Phase 2 起正式落库。
 */
@Data
@TableName("skill_call_log")
public class SkillCallLogDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workspaceId;
    private String agentId;
    private String skillId;
    private String sessionId;
    private String traceId;

    /** JSONB：调用入参 */
    private String inputParams;

    /** JSONB：调用出参（失败时为空对象） */
    private String outputResult;

    private Long durationMs;
    private Integer tokenCount;

    /** success / failed / timeout */
    private String status;

    private String errorMessage;
    private LocalDateTime createdAt;
}
