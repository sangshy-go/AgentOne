package com.agentone.skill.vo;

import lombok.Data;

import java.util.List;

/**
 * 调试向导第 2 步：参数预检结果 + 执行计划预览
 */
@Data
public class DebugPreviewVO {

    /** 参数校验是否通过 */
    private boolean valid;

    /** 校验错误列表（valid=false 时非空） */
    private List<String> errors;

    /** 执行计划预览（如 "HTTP GET https://..."，不落真实请求） */
    private String plan;
}
