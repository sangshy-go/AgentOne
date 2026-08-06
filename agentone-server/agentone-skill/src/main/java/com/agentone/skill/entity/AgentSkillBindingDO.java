package com.agentone.skill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent-Skill 绑定实体
 */
@Data
@TableName("agent_skill_binding")
public class AgentSkillBindingDO {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 所属工作空间（S3：纳入多租户隔离，防止越权绑定其他空间的 Skill） */
    private String workspaceId;

    private String agentId;
    private String skillId;
    private String skillVersion;
    private String configOverride;  // JSONB
    private Boolean enabled;
    private LocalDateTime createdAt;
}
