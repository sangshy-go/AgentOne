package com.agentone.workspace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作空间实体
 */
@Data
@TableName("workspace")
public class WorkspaceDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;
    private String description;
    private String ownerId;
    private String settings;  // JSONB 在 MyBatis-Plus 中作为 String 处理
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /**
     * 软删除标记（时间戳型）：value="null" 使查询自动追加 deleted_at IS NULL，
     * delval="now()" 使 deleteById 执行 UPDATE ... SET deleted_at = now()。
     * 不能用默认的 0/1 语义（会生成 deleted_at = 0，与 timestamp 类型不兼容）
     */
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}
