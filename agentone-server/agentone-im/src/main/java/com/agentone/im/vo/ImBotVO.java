package com.agentone.im.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * IM 机器人展示对象。
 * 安全约束：config 密文绝不回传，只返回掩码摘要（如 access_token 前 6 位）
 */
@Data
public class ImBotVO {

    private String id;

    private String name;

    private String platform;

    private String mode;

    private String agentId;

    private String status;

    private String createdBy;

    private LocalDateTime createdAt;

    /** 配置掩码摘要：键同 config，值为掩码后的可展示片段 */
    private Map<String, String> configMasked;
}
