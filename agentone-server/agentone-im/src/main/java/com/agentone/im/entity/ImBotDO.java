package com.agentone.im.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 机器人（课题⑤）
 * config 为 AES-GCM 密文：凭证是第三方密钥，明文落库等于泄露发送能力
 */
@Data
@TableName("im_bot")
public class ImBotDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workspaceId;

    private String name;

    /** dingtalk / wecom */
    private String platform;

    /** webhook（仅发送）/ callback（收发） */
    private String mode;

    /** 绑定的 Agent，可空（纯通知机器人） */
    private String agentId;

    /** 配置密文（AES-GCM，密钥来自 AGENTONE_IM_SECRET_KEY） */
    private String configEncrypted;

    /** active / disabled */
    private String status;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
