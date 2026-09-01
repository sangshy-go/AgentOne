package com.agentone.agent.dto;

import lombok.Data;

/**
 * 驳回发布审批请求
 * comment 不用 @NotBlank 校验：缺失时须返回业务错误码 8004（驳回必须填写理由），
 * 而非 400 参数校验错误
 */
@Data
public class RejectPublishRequestDTO {

    private String comment;
}
