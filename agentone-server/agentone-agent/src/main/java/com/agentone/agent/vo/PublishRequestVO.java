package com.agentone.agent.vo;

import com.agentone.agent.enums.AgentStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发布审批申请单展示对象
 */
@Data
public class PublishRequestVO {

    private String id;
    private String agentId;

    /** 提交时名称快照（Agent 删除后仍可追溯） */
    private String agentName;

    /** Agent 当前状态（Agent 已删除时为 null） */
    private AgentStatus agentStatus;

    /** pending / approved / rejected / withdrawn */
    private String status;

    private String submitterId;
    private String submitterEmail;
    private String reviewerId;
    private String reviewerEmail;
    private String reviewComment;

    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
}
