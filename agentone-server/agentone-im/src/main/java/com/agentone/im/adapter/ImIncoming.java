package com.agentone.im.adapter;

/**
 * 平台无关的统一入站消息
 *
 * @param senderId         平台内发送者唯一标识（钉钉 senderStaffId / 企微 FromUserName）
 * @param senderName       发送者昵称（可空）
 * @param text             文本内容
 * @param conversationType 1=单聊 2=群聊（平台语义对齐）
 */
public record ImIncoming(String senderId, String senderName, String text, String conversationType) {
}
