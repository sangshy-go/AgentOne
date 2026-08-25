package com.agentone.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 对话请求 DTO
 *
 * 注意：message 不再强制 @NotBlank，改由 Service 层校验「message 与 attachmentIds 至少其一」，
 * 以支持"仅发送附件（无文字）"的场景；同时保持与 /v1/chat 共用此 DTO。
 */
@Data
public class ChatRequestDTO {

    /** Agent ID */
    @NotBlank(message = "agentId 不能为空")
    private String agentId;

    /** 会话 ID（可选，为空则自动创建） */
    private String sessionId;

    /** 用户输入（与 attachmentIds 至少其一） */
    private String message;

    /** 附件 ID 列表（与 message 至少其一） */
    private List<String> attachmentIds;

    /** 是否流式返回 */
    private boolean stream;
}
