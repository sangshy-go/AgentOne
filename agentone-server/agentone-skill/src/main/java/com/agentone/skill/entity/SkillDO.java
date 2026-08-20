package com.agentone.skill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Skill 实体
 */
@Data
@TableName("skill")
public class SkillDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workspaceId;
    private String name;
    private String type;        // builtin / api / prompt / function / mcp / market
    private String source;
    private String category;    // 业务分类（课题⑧，受控词表，缺省"其他"）
    private String description;
    private String inputSchema;   // JSONB
    private String outputSchema;  // JSONB
    private String config;        // JSONB
    private String version;
    private String status;        // active / disabled / deprecated
    private LocalDateTime installedAt;
}
