package com.agentone.workspace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-工作空间关联实体
 */
@Data
@TableName("user_workspace")
public class UserWorkspaceDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String workspaceId;
    private String role;
    private LocalDateTime createdAt;
}
