package com.agentone.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型供应商实体
 */
@Data
@TableName("model_provider")
public class ModelProviderDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workspaceId;
    private String name;
    private String provider;
    private String apiKey;
    private String baseUrl;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
