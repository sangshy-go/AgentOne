package com.agentone.im.dto;

import lombok.Data;

import java.util.Map;

/**
 * 更新 IM 机器人（字段均可选；config 传了则整体替换）
 */
@Data
public class ImBotUpdateDTO {

    private String name;

    private String agentId;

    /** active / disabled */
    private String status;

    private Map<String, String> config;
}
