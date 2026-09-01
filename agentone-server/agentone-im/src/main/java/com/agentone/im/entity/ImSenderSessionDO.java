package com.agentone.im.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * IM 发送者→会话映射：同一发送者的消息落入同一会话，保证多轮记忆
 */
@Data
@TableName("im_sender_session")
public class ImSenderSessionDO {

    private String botId;

    private String senderId;

    private String sessionId;
}
