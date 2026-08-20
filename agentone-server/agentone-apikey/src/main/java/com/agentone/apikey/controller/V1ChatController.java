package com.agentone.apikey.controller;

import com.agentone.agent.dto.ChatRequestDTO;
import com.agentone.agent.service.ChatService;
import com.agentone.agent.vo.ChatResponseVO;
import com.agentone.apikey.entity.ApiKeyDO;
import com.agentone.apikey.mapper.ApiKeyMapper;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.common.result.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;

/**
 * 外部 API - 对话接口（/v1/chat）
 * 通过 X-API-Key 认证，由 ApiKeyAuthFilter 处理
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class V1ChatController {

    private final ChatService chatService;
    private final ApiKeyMapper apiKeyMapper;
    private final ObjectMapper objectMapper;

    /**
     * 同步对话
     */
    @PostMapping("/chat")
    public Result<ChatResponseVO> chat(@Valid @RequestBody ChatRequestDTO dto) {
        // 同步接口不支持流式：客户端传 stream=true 时明确拒绝，避免静默忽略导致契约不符
        if (dto.isStream()) {
            throw new BusinessException(400, "同步 /v1/chat 不支持流式，请改用 /v1/chat/stream");
        }
        checkAgentPermission(dto.getAgentId());
        return Result.ok(chatService.chat(dto));
    }

    /**
     * 流式对话（SSE）
     * 事件类型：session / delta / done / error
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@Valid @RequestBody ChatRequestDTO dto) {
        checkAgentPermission(dto.getAgentId());
        return chatService.chatStream(dto);
    }

    /**
     * 校验当前 API Key 是否有权调用指定 Agent
     */
    private void checkAgentPermission(String agentId) {
        String userId = RuntimeContext.getUserId();
        if (userId == null || !userId.startsWith("apikey:")) {
            return;
        }
        String apiKeyId = userId.substring("apikey:".length());
        ApiKeyDO apiKey = apiKeyMapper.selectById(apiKeyId);
        if (apiKey == null) {
            throw new BusinessException(401, "API Key 无效");
        }

        List<String> allowed = parseAllowedAgents(apiKey.getAllowedAgents());
        if (!allowed.isEmpty() && !allowed.contains(agentId)) {
            throw new BusinessException(403, "该 API Key 无权调用此 Agent");
        }
    }

    private List<String> parseAllowedAgents(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            // fail-closed：allowedAgents 字段损坏时拒绝请求，而非误判为"全部放行"
            throw new BusinessException(403, "API Key 的 allowedAgents 配置格式非法");
        }
    }
}
