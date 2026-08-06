package com.agentone.apikey.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * API Key 实体
 */
@Data
@TableName("api_key")
public class ApiKeyDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workspaceId;

    /** SHA-256 哈希（不存明文） */
    private String keyHash;

    /** 前 8 位前缀，用于页面展示 */
    private String keyPrefix;

    /** live / test */
    private String env;

    /** active / disabled / deleted */
    private String status;

    /** JSONB - 允许调用的 Agent ID 列表 */
    private String allowedAgents;

    /** 每日调用上限 */
    private Integer dailyLimit;

    private LocalDateTime createdAt;
}
