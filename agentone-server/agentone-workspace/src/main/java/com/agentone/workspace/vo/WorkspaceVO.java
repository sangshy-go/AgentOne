package com.agentone.workspace.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作空间 VO
 */
@Data
public class WorkspaceVO {

    private String id;
    private String name;
    private String description;
    private String role;  // 当前用户在该空间的角色
    private LocalDateTime createdAt;
}
