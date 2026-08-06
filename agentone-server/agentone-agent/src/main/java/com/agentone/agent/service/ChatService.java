package com.agentone.agent.service;

import com.agentone.agent.dto.ChatRequestDTO;
import com.agentone.agent.vo.ChatMessageVO;
import com.agentone.agent.vo.ChatResponseVO;
import com.agentone.agent.vo.ChatSessionVO;
import com.agentone.common.result.PageResult;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 对话服务
 */
public interface ChatService {

    /**
     * 同步对话
     */
    ChatResponseVO chat(ChatRequestDTO dto);

    /**
     * 流式对话（SSE）
     *
     * 事件类型：
     * - event: session, data: <sessionId>    新建会话时推送
     * - event: delta,   data: <text chunk>   文本增量
     * - event: done,    data: [DONE]         流结束
     * - event: error,   data: <message>      流异常
     */
    Flux<ServerSentEvent<String>> chatStream(ChatRequestDTO dto);

    /**
     * 获取会话列表
     */
    PageResult<ChatSessionVO> listSessions(String agentId, Integer page, Integer size);

    /**
     * 获取会话详情（含消息）
     */
    ChatSessionVO getSession(String sessionId);

    /**
     * 获取会话消息列表
     */
    List<ChatMessageVO> getSessionMessages(String sessionId);

    /**
     * 重命名会话
     */
    void renameSession(String sessionId, String title);

    /**
     * 删除会话
     */
    void deleteSession(String sessionId);
}
