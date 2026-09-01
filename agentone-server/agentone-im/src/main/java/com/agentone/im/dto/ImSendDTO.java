package com.agentone.im.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * IM 主动发送（测试/通知）
 */
@Data
public class ImSendDTO {

    @NotBlank(message = "text 不能为空")
    private String text;

    /** text / markdown，默认 text */
    private String msgType;

    /** markdown 标题 */
    private String title;
}
