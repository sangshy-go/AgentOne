package com.agentone.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 提交发布审批请求
 */
@Data
public class SubmitPublishRequestDTO {

    @NotBlank(message = "agentId 不能为空")
    private String agentId;
}
