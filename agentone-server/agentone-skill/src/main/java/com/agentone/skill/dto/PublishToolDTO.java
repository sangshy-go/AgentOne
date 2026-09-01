package com.agentone.skill.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * MCP 工具发布开关请求体
 */
@Data
public class PublishToolDTO {

    /** true=发布到广场（可被绑定/启用）；false=撤回 */
    @NotNull
    private Boolean published;
}
