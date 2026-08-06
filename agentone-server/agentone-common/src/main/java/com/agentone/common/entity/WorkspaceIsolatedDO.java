package com.agentone.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 带 workspace_id 的基础实体 - 需要数据隔离的表继承此类
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class WorkspaceIsolatedDO extends BaseDO {

    @TableField(fill = FieldFill.INSERT)
    private String workspaceId;
}
