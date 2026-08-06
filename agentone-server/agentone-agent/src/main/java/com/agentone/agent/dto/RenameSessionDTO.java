package com.agentone.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重命名会话 DTO
 */
@Data
public class RenameSessionDTO {

    @NotBlank(message = "会话标题不能为空")
    @Size(max = 100, message = "会话标题不能超过100个字符")
    private String title;
}
