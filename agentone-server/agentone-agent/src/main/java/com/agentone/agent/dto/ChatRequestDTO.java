package com.agentone.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 对话请求 DTO
 */
@Data
public class ChatRequestDTO {

    /** Agent ID */
    @NotBlank(message = "agentId 不能为空")
    private String agentId;

    /** 会话 ID（可选，为空则自动创建） */
    private String sessionId;

    /** 用户输入 */
    @NotBlank(message = "消息内容不能为空")
    private String message;

    /** 是否流式返回 */
    private boolean stream;
}
