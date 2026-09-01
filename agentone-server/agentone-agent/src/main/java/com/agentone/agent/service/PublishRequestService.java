package com.agentone.agent.service;

import com.agentone.agent.dto.RejectPublishRequestDTO;
import com.agentone.agent.dto.SubmitPublishRequestDTO;
import com.agentone.agent.vo.PublishRequestVO;
import com.agentone.common.result.PageResult;

/**
 * Agent 发布审批服务（课题⑩）
 * 双人原则：提交人不可审批自己的申请；驳回必须填写理由。
 * 错误码段 8001-8009。
 */
public interface PublishRequestService {

    String STATUS_PENDING = "pending";
    String STATUS_APPROVED = "approved";
    String STATUS_REJECTED = "rejected";
    String STATUS_WITHDRAWN = "withdrawn";

    /** 提交发布审批（仅 DRAFT/TESTING 可提交，同 Agent 同时只允许一条待审） */
    PublishRequestVO submit(SubmitPublishRequestDTO dto);

    /** 列表：admin/owner/auditor 看全空间，其余角色只看自己提交的 */
    PageResult<PublishRequestVO> list(String status, String agentId, Integer page, Integer size);

    /** 审批通过（仅 admin/owner，且非提交人本人）→ Agent 发布 + 版本号+1 */
    PublishRequestVO approve(String id);

    /** 驳回（仅 admin/owner，且非提交人本人，理由必填）→ Agent 退回草稿 */
    PublishRequestVO reject(String id, RejectPublishRequestDTO dto);

    /** 撤回（仅提交人本人，仅待审中）→ Agent 退回草稿 */
    PublishRequestVO withdraw(String id);
}
