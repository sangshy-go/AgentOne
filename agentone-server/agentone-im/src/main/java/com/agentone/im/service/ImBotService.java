package com.agentone.im.service;

import com.agentone.im.adapter.ImIncoming;
import com.agentone.im.dto.ImBotCreateDTO;
import com.agentone.im.dto.ImBotUpdateDTO;
import com.agentone.im.dto.ImSendDTO;
import com.agentone.im.vo.ImBotVO;

import java.util.List;

/**
 * IM Bot 网关服务（课题⑤）
 */
public interface ImBotService {

    /** 当前工作空间的机器人列表 */
    List<ImBotVO> list();

    /** 创建机器人（凭证加密存储） */
    ImBotVO create(ImBotCreateDTO dto);

    /** 更新（config 传了则整体验证并替换） */
    ImBotVO update(String id, ImBotUpdateDTO dto);

    /** 删除（级联清理发送者会话映射） */
    void delete(String id);

    /** 主动发送（webhook 模式；企微发送暂不支持，见实现说明） */
    void send(String id, ImSendDTO dto);

    /**
     * 回调收消息统一入口：校验 → 注入运行时上下文 → 同步对话 → 返回回复文本。
     * 返回空串表示无需回复（机器人停用/非 callback 模式等）。
     */
    String handleIncoming(String botId, ImIncoming incoming);
}
