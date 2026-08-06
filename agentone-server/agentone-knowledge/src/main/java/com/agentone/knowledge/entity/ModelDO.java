package com.agentone.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型实体（两层结构：Provider → Model）
 * 一个 provider 下可以有多个模型（chat / embedding / rerank）
 */
@Data
@TableName("model")
public class ModelDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String providerId;
    /** 模型类型：chat | embedding | rerank | image2text */
    private String modelType;
    /** API 调用时的 model 字段值（如 qwen3.7-max、text-embedding-v3） */
    private String modelId;
    /** 显示名称 */
    private String displayName;
    /** 上下文长度 */
    private Integer contextSize;
    /** 最大响应长度（仅 chat 类型） */
    private Integer maxTokens;
    /** 向量维度（仅 embedding 类型，由系统首次使用时自动探测，无需用户填写） */
    @TableField(insertStrategy = FieldStrategy.ALWAYS)
    private Integer dimensions;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
