package com.agentone.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

/**
 * 创建 IM 机器人
 * config 键按平台区分：
 * - dingtalk + webhook：webhookUrl（必填）、secret（加签可选）
 * - dingtalk + callback：appSecret（企业机器人回调验签）
 * - wecom + callback：corpId、agentId、secret、token、encodingAesKey
 * - wecom + webhook：无此形态，服务端拒绝
 */
@Data
public class ImBotCreateDTO {

    @NotBlank(message = "name 不能为空")
    private String name;

    @NotBlank(message = "platform 不能为空")
    private String platform;

    @NotBlank(message = "mode 不能为空")
    private String mode;

    /** 绑定的 Agent，可空（纯通知机器人） */
    private String agentId;

    @NotEmpty(message = "config 不能为空")
    private Map<String, String> config;
}
