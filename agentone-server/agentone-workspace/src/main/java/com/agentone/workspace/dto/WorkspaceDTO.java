package com.agentone.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 工作空间创建/更新 DTO
 */
@Data
public class WorkspaceDTO {

    @NotBlank(message = "工作空间名称不能为空")
    @Size(max = 100, message = "名称最长 100 字")
    private String name;

    @Size(max = 500, message = "描述最长 500 字")
    private String description;
}
