package com.agentone.agent.controller;

import com.agentone.agent.dto.ChatRequestDTO;
import com.agentone.agent.dto.RenameSessionDTO;
import com.agentone.agent.service.ChatService;
import com.agentone.agent.vo.ChatMessageVO;
import com.agentone.agent.vo.ChatResponseVO;
import com.agentone.agent.vo.ChatSessionVO;
import com.agentone.common.result.PageResult;
import com.agentone.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 对话 Controller
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 同步对话
     */
    @PostMapping
    public Result<ChatResponseVO> chat(@Valid @RequestBody ChatRequestDTO dto) {
        return Result.ok(chatService.chat(dto));
    }

    /**
     * 流式对话（SSE）
     * 事件类型：session / delta / done / error
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@Valid @RequestBody ChatRequestDTO dto) {
        return chatService.chatStream(dto);
    }

    /**
     * 获取 Agent 的会话列表
     */
    @GetMapping("/sessions")
    public Result<PageResult<ChatSessionVO>> listSessions(
            @RequestParam String agentId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.ok(chatService.listSessions(agentId, page, size));
    }

    /**
     * 获取会话详情
     */
    @GetMapping("/sessions/{sessionId}")
    public Result<ChatSessionVO> getSession(@PathVariable String sessionId) {
        return Result.ok(chatService.getSession(sessionId));
    }

    /**
     * 获取会话消息列表
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessageVO>> getSessionMessages(@PathVariable String sessionId) {
        return Result.ok(chatService.getSessionMessages(sessionId));
    }

    /**
     * 重命名会话
     */
    @PutMapping("/sessions/{sessionId}/rename")
    public Result<Void> renameSession(@PathVariable String sessionId,
                                       @Valid @RequestBody RenameSessionDTO dto) {
        chatService.renameSession(sessionId, dto.getTitle());
        return Result.ok();
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        chatService.deleteSession(sessionId);
        return Result.ok();
    }
}
