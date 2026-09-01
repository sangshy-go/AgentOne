package com.agentone.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 发布审批申请单（课题⑩）
 * 双人原则：提交人/审核人分离，提交人不可审批自己的申请。
 * agent_name/submitter_email/reviewer_email 为时点快照：审计记录自包含，
 * Agent 后续被删除不影响历史申请的可追溯性。
 */
@Data
@TableName("agent_publish_request")
public class AgentPublishRequestDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workspaceId;

    private String agentId;

    /** 提交时 Agent 名称快照 */
    private String agentName;

    /** JSONB - 提交时 Agent 全量配置快照（审什么 = 发什么） */
    private String configSnapshot;

    /** pending / approved / rejected / withdrawn */
    private String status;

    private String submitterId;

    private String submitterEmail;

    private String reviewerId;

    private String reviewerEmail;

    /** 驳回理由（必填）/ 审批意见 */
    private String reviewComment;

    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;
}
