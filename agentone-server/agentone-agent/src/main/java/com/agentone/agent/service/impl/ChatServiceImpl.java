package com.agentone.agent.service.impl;

import com.agentone.agent.config.AgentScopeConfig;
import com.agentone.agent.dto.ChatRequestDTO;
import com.agentone.agent.entity.AgentDO;
import com.agentone.agent.entity.ChatAttachmentDO;
import com.agentone.agent.entity.ChatMessageDO;
import com.agentone.agent.entity.ChatSessionDO;
import com.agentone.agent.enums.AgentStatus;
import com.agentone.agent.mapper.AgentMapper;
import com.agentone.agent.mapper.ChatAttachmentMapper;
import com.agentone.agent.mapper.ChatMessageMapper;
import com.agentone.agent.mapper.ChatSessionMapper;
import com.agentone.agent.memory.MemoryService;
import com.agentone.agent.model.MemoryConfig;
import com.agentone.agent.model.ModelConfig;
import com.agentone.agent.persona.VariableInjector;
import com.agentone.agent.service.ChatAttachmentService;
import com.agentone.agent.service.ChatService;
import com.agentone.agent.vo.ChatMessageVO;
import com.agentone.agent.vo.ChatResponseVO;
import com.agentone.agent.vo.ChatSessionVO;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.common.result.ResultCode;
import com.agentone.common.util.TokenCounter;
import com.agentone.knowledge.entity.ModelDO;
import com.agentone.knowledge.entity.ModelProviderDO;
import com.agentone.knowledge.mapper.ModelMapper;
import com.agentone.knowledge.mapper.ModelProviderMapper;
import com.agentone.knowledge.service.KnowledgeService;
import com.agentone.knowledge.vo.AgentKnowledgeBindingVO;
import com.agentone.knowledge.vo.SearchResultVO;
import com.agentone.agent.tool.SkillAgentTool;
import com.agentone.skill.core.SkillCallLogRecorder;
import com.agentone.skill.core.SkillRegistry;
import com.agentone.skill.service.AgentSkillService;
import com.agentone.skill.vo.AgentSkillBindingVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.agentone.common.result.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 对话服务实现
 * 使用 AgentScope Java 2.0 的 ReActAgent 引擎
 *
 * 核心流程:
 * 1. 加载 Agent 配置
 * 2. 获取/创建会话
 * 3. 构建 AGENTS.md 系统提示词 + 变量注入
 * 4. 创建 ReActAgent（每请求一个实例，非线程安全）
 * 5. 调用 agent.call() 或 agent.streamEvents()
 * 6. 保存消息 + 更新会话统计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final AgentMapper agentMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final VariableInjector variableInjector;
    private final MemoryService memoryService;
    private final AgentSkillService agentSkillService;
    private final SkillRegistry skillRegistry;
    private final SkillCallLogRecorder skillCallLogRecorder;
    private final KnowledgeService knowledgeService;
    private final ModelMapper modelMapper;
    private final ModelProviderMapper modelProviderMapper;
    private final Model model;  // AgentScope 自动配置的 Model Bean
    private final ObjectMapper objectMapper;
    private final AgentScopeConfig agentScopeConfig;
    private final ChatAttachmentService attachmentService;
    private final ChatAttachmentMapper attachmentMapper;

    /** Q6: ReAct 最大迭代次数（原硬编码 10），可通过 agentone.chat.max-iters 覆盖 */
    @org.springframework.beans.factory.annotation.Value("${agentone.chat.max-iters:10}")
    private int maxIters;

    /** Q6: RAG 上下文最大 token 预算（原硬编码 3000），可通过 agentone.chat.rag-max-tokens 覆盖 */
    @org.springframework.beans.factory.annotation.Value("${agentone.chat.rag-max-tokens:3000}")
    private int ragMaxTokens;

    /** 附件（文档解析文本）注入本轮对话的 token 上限 */
    @org.springframework.beans.factory.annotation.Value("${agentone.chat.attachment-max-tokens:6000}")
    private int attachmentMaxTokens;

    @Override
    public ChatResponseVO chat(ChatRequestDTO dto) {
        long startTime = System.currentTimeMillis();

        // 1. 加载 Agent，未配置任何可用模型时提前失败（6018），避免回退占位配置产生晦涩网络错误
        AgentDO agentDO = loadAgent(dto.getAgentId());
        ensureModelConfigured(agentDO);

        // 2. 获取或创建会话
        ChatSessionDO session = getOrCreateSession(dto.getSessionId(), agentDO);

        // 3. 构建用户消息内容（校验附件归属、多模态 blocks + 附件元信息 JSON）
        UserContent userContent = buildUserContent(dto);

        // 4. 先保存用户消息（RAG 检索需要当前消息作为 query；落库 content 保持纯文本）
        saveUserMessage(session.getId(), nullSafe(dto.getMessage()), userContent.attachmentsJson());

        // 5~10. 构建上下文/引擎并同步调用；失败时补偿 assistant 错误占位，保持配对（Bug5）
        String reply;
        int totalTokens;
        try {
            // 5. 构建系统提示词（AGENTS.md + 变量注入 + RAG + 历史上下文）
            String systemPrompt = buildSystemPrompt(agentDO, session.getId());

            // 6. 创建 ReActAgent（每请求一个实例；sink 收集本轮 Skill 调用）
            List<Map<String, Object>> toolCalls = new java.util.concurrent.CopyOnWriteArrayList<>();
            ReActAgent agent = buildReActAgent(agentDO, systemPrompt, session.getId(), toolCalls);

            // 7. 构建 AgentScope RuntimeContext
            io.agentscope.core.agent.RuntimeContext agentCtx = io.agentscope.core.agent.RuntimeContext.builder()
                    .sessionId(session.getId())
                    .userId(RuntimeContext.getUserId())
                    .put("workspace_id", RuntimeContext.getWorkspaceId())
                    .put("agent_id", agentDO.getId())
                    .build();

            // 8. 调用 AgentScope（同步）
            Msg userMsg = Msg.builder()
                    .name("user")
                    .role(MsgRole.USER)
                    .content(userContent.blocks())
                    .build();

            // 同步调用增加有界超时，避免 429/重试时长期占用 Tomcat 线程（Bug8）
            // 超时抛出的 ReactorException 由下方 catch(RuntimeException) 统一处理并落库
            Msg response = agent.call(List.of(userMsg), agentCtx)
                    .block(Duration.ofMinutes(2));
            reply = response != null ? response.getTextContent() : "";

            // 8. 保存助手回复（含本轮 Skill 调用快照 + 耗时/traceId）
            long durationMs = System.currentTimeMillis() - startTime;
            saveMessage(session.getId(), "assistant", reply, serializeToolCalls(toolCalls), (int) durationMs);

            totalTokens = estimateTokens(nullSafe(dto.getMessage()) + reply);

            // 9. 原子更新会话统计（避免并发对话同一 session 时 messageCount 自增丢更新）
            incrementSessionStats(session.getId(), totalTokens);
        } catch (RuntimeException e) {
            // 模型/引擎调用失败：保存 assistant 错误占位，避免留下孤立 user 消息（Bug5）
            saveAssistantError(session.getId(), RuntimeContext.getUserId(),
                    RuntimeContext.getWorkspaceId(), "[错误: " + safeMsg(e) + "]");
            throw e;
        }

        // 10. 返回响应
        ChatResponseVO vo = new ChatResponseVO();
        vo.setSessionId(session.getId());
        vo.setReply(reply);
        vo.setTokenCount(totalTokens);
        vo.setDurationMs((int) (System.currentTimeMillis() - startTime));
        vo.setTraceId(UUID.randomUUID().toString().substring(0, 8));
        return vo;
    }

    @Override
    public Flux<ServerSentEvent<String>> chatStream(ChatRequestDTO dto) {
        // 整体流程（含同步阶段）包在 Flux.defer 中：
        // 1) 同步阶段抛出的 BusinessException（如 buildReActAgent 校验失败）会被 onErrorResume 捕获，
        //    转为 SSE error 事件，而非让已设置的 text/event-stream 返回无法解析的 JSON（Bug6）
        // 2) 任何阶段出错，最终都保证发出 error + done 事件，避免前端一直等待（Bug2）
        return Flux.defer(() -> {
            long streamStart = System.currentTimeMillis();
            // 1. 加载 Agent + 会话
            // 未配置任何可用模型时提前失败（6018）：置于会话/消息落库之前，避免产生垃圾会话；
            // 异常由外层 onErrorResume 转为 SSE error 事件（Bug6 通道）
            AgentDO agentDO = loadAgent(dto.getAgentId());
            ensureModelConfigured(agentDO);
            ChatSessionDO session = getOrCreateSession(dto.getSessionId(), agentDO);

            // 2. 捕获当前请求上下文（SSE doOnComplete 触发时 ThreadLocal 已被清除）
            String capturedUserId = RuntimeContext.getUserId();
            String capturedWorkspaceId = RuntimeContext.getWorkspaceId();

            // 3. 构建用户消息内容（校验附件归属、多模态 blocks + 附件元信息 JSON）
            UserContent userContent = buildUserContent(dto);

            // 4. 先保存用户消息（RAG 检索需要当前消息作为 query；落库 content 保持纯文本）
            saveUserMessage(session.getId(), nullSafe(dto.getMessage()), userContent.attachmentsJson());

            // 5. 构建上下文（AGENTS.md + RAG + 历史）
            ReActAgent agent;
            io.agentscope.core.agent.RuntimeContext agentCtx;
            List<Map<String, Object>> toolCalls = new java.util.concurrent.CopyOnWriteArrayList<>();
            try {
                String systemPrompt = buildSystemPrompt(agentDO, session.getId());
                agent = buildReActAgent(agentDO, systemPrompt, session.getId(), toolCalls);
                agentCtx = io.agentscope.core.agent.RuntimeContext.builder()
                        .sessionId(session.getId())
                        .userId(capturedUserId)
                        .put("workspace_id", capturedWorkspaceId)
                        .put("agent_id", agentDO.getId())
                        .build();
            } catch (RuntimeException e) {
                // 同步阶段异常：补偿一条 assistant 错误占位，避免留下孤立 user 消息（Bug5）
                saveAssistantError(session.getId(), capturedUserId, capturedWorkspaceId,
                        "[错误: " + safeMsg(e) + "]");
                throw e;
            }

            // 6. 构建用户消息
            Msg userMsg = Msg.builder()
                    .name("user")
                    .role(MsgRole.USER)
                    .content(userContent.blocks())
                    .build();

            // 6. 流式调用 AgentScope streamEvents()，包装为 SSE 事件
            StringBuilder fullReply = new StringBuilder();

            // 第一个事件：推送 sessionId（前端新建会话时需要知道 sessionId）
            ServerSentEvent<String> sessionEvent = ServerSentEvent.<String>builder()
                    .event("session")
                    .data(session.getId())
                    .build();

            Flux<ServerSentEvent<String>> deltaFlux = agent.streamEvents(userMsg, agentCtx)
                    .<ServerSentEvent<String>>handle((event, sink) -> {
                        if (event instanceof TextBlockDeltaEvent textEvent) {
                            String chunk = textEvent.getDelta();
                            if (chunk != null && !chunk.isEmpty()) {
                                fullReply.append(chunk);
                                sink.next(ServerSentEvent.<String>builder()
                                        .event("delta")
                                        .data(chunk)
                                        .build());
                            }
                        } else if (event instanceof ThinkingBlockDeltaEvent thinkingEvent) {
                            String chunk = thinkingEvent.getDelta();
                            if (chunk != null && !chunk.isEmpty()) {
                                sink.next(ServerSentEvent.<String>builder()
                                        .event("thinking")
                                        .data(chunk)
                                        .build());
                            }
                        }
                        // 其他事件类型（ModelCallStartEvent、BlockStartEvent 等）静默忽略
                    })
                    .doOnComplete(() -> {
                        // 流结束后保存完整回复
                        // 注意：此时 RuntimeContext 已被 JwtAuthFilter 清除，需临时恢复以便 MyBatis 填充
                        try {
                            com.agentone.common.context.Context ctx =
                                    com.agentone.common.context.Context.of(capturedUserId, capturedWorkspaceId);
                            RuntimeContext.set(ctx);

                            saveMessage(session.getId(), "assistant", fullReply.toString(),
                                    serializeToolCalls(toolCalls),
                                    (int) (System.currentTimeMillis() - streamStart));
                            // 原子自增统计：message_count +2，token_count 累加（Bug3）
                            incrementSessionStats(session.getId(),
                                    estimateTokens(nullSafe(dto.getMessage()) + fullReply.toString()));
                        } finally {
                            RuntimeContext.clear();
                        }
                    })
                    .doOnError(error -> {
                        // 流中途失败：记录日志，并始终补偿一条 assistant 占位消息（Bug5）
                        // 即使没有任何 delta，也要落一条 assistant 消息，保持 user/assistant 配对
                        log.error("流式对话失败: sessionId={}, error={}", session.getId(), error.getMessage());
                        String content = fullReply.length() > 0
                                ? fullReply.toString() + "\n\n[错误: " + safeMsg(error) + "]"
                                : "[错误: " + safeMsg(error) + "]";
                        try {
                            com.agentone.common.context.Context ctx =
                                    com.agentone.common.context.Context.of(capturedUserId, capturedWorkspaceId);
                            RuntimeContext.set(ctx);
                            saveMessage(session.getId(), "assistant", content, null,
                                    (int) (System.currentTimeMillis() - streamStart));
                            incrementSessionStats(session.getId(),
                                    estimateTokens(nullSafe(dto.getMessage()) + content));
                        } catch (Exception ignore) {
                            log.warn("流式失败补偿消息落库异常: {}", ignore.getMessage());
                        } finally {
                            RuntimeContext.clear();
                        }
                    });

            // 结束事件：[DONE]
            ServerSentEvent<String> doneEvent = ServerSentEvent.<String>builder()
                    .event("done")
                    .data("[DONE]")
                    .build();

            // 拼接：session → deltas → done
            return Flux.concat(Flux.just(sessionEvent), deltaFlux, Flux.just(doneEvent));
        })
        .onErrorResume(error -> {
            // 始终发出 error + done，避免前端一直等待（Bug2）；
            // 同步阶段异常（如 BusinessException）也走这里转为 SSE error 事件（Bug6）
            ServerSentEvent<String> errEvent = ServerSentEvent.<String>builder()
                    .event("error")
                    .data(safeMsg(error))
                    .build();
            ServerSentEvent<String> doneEvent = ServerSentEvent.<String>builder()
                    .event("done")
                    .data("[DONE]")
                    .build();
            return Flux.just(errEvent, doneEvent);
        });
    }

    @Override
    public PageResult<ChatSessionVO> listSessions(String agentId, Integer page, Integer size) {
        String workspaceId = RuntimeContext.getWorkspaceId();
        Page<ChatSessionDO> p = new Page<>(page, size);
        Page<ChatSessionDO> result = sessionMapper.selectPage(p,
                new LambdaQueryWrapper<ChatSessionDO>()
                        .eq(ChatSessionDO::getAgentId, agentId)
                        .eq(ChatSessionDO::getWorkspaceId, workspaceId)
                        .orderByDesc(ChatSessionDO::getUpdatedAt));
        Page<ChatSessionVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toSessionVO).collect(Collectors.toList()));
        return PageResult.of(voPage);
    }

    @Override
    public ChatSessionVO getSession(String sessionId) {
        ChatSessionDO session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(4002, "会话不存在");
        }
        // 校验会话归属权
        String currentUserId = RuntimeContext.getUserId();
        if (!session.getUserId().equals(currentUserId)) {
            throw new BusinessException(4003, "无权访问该会话");
        }
        return toSessionVO(session);
    }

    @Override
    public List<ChatMessageVO> getSessionMessages(String sessionId) {
        ChatSessionDO session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(4002, "会话不存在");
        }
        // 校验会话归属权
        String currentUserId = RuntimeContext.getUserId();
        if (!session.getUserId().equals(currentUserId)) {
            throw new BusinessException(4003, "无权访问该会话");
        }

        List<ChatMessageDO> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageDO>()
                        .eq(ChatMessageDO::getSessionId, sessionId)
                        .orderByAsc(ChatMessageDO::getCreatedAt)
        );
        return messages.stream().map(this::toMessageVO).collect(Collectors.toList());
    }

    @Override
    public void renameSession(String sessionId, String title) {
        ChatSessionDO session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(4002, "会话不存在");
        }
        // 校验会话归属权
        String currentUserId = RuntimeContext.getUserId();
        if (!session.getUserId().equals(currentUserId)) {
            throw new BusinessException(4003, "无权访问该会话");
        }
        session.setTitle(title);
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    @Override
    public void deleteSession(String sessionId) {
        ChatSessionDO session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(4002, "会话不存在");
        }
        // 校验会话归属权
        String currentUserId = RuntimeContext.getUserId();
        if (!session.getUserId().equals(currentUserId)) {
            throw new BusinessException(4003, "无权访问该会话");
        }
        List<ChatMessageDO> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageDO>()
                        .eq(ChatMessageDO::getSessionId, sessionId)
                        .select(ChatMessageDO::getId, ChatMessageDO::getAttachments));
        List<String> attachmentIds = messages.stream()
                .map(ChatMessageDO::getAttachments)
                .filter(Objects::nonNull)
                .flatMap(json -> parseAttachmentIds(json).stream())
                .distinct()
                .toList();
        if (!attachmentIds.isEmpty()) {
            attachmentMapper.deleteBatchIds(attachmentIds);
        }
        messageMapper.delete(
                new LambdaQueryWrapper<ChatMessageDO>()
                        .eq(ChatMessageDO::getSessionId, sessionId)
        );
        sessionMapper.deleteById(sessionId);
    }

    // ==================== 内部方法 ====================

    /**
     * 构建用户消息内容块 + 附件元信息 JSON。
     *
     * 校验：message 与 attachmentIds 至少其一；/v1 调用（userId 以 apikey: 开头）带附件返回 4015。
     * 图片走 ImageBlock(Base64Source)，文档解析文本按 token 预算截断后走 TextBlock（前缀「[附件: fileName]」），
     * 落库 content 保持纯文本，不污染 RAG query 与历史上下文。
     */
    private UserContent buildUserContent(ChatRequestDTO dto) {
        boolean hasText = dto.getMessage() != null && !dto.getMessage().isBlank();
        boolean hasAttach = dto.getAttachmentIds() != null && !dto.getAttachmentIds().isEmpty();
        if (!hasText && !hasAttach) {
            throw new BusinessException(4001, "请输入消息或上传附件");
        }
        if (RuntimeContext.getUserId().startsWith("apikey:") && hasAttach) {
            throw new BusinessException(4015, "API Key 调用不支持附件上传，请使用 /api 路径");
        }

        List<ContentBlock> blocks = new ArrayList<>();
        List<Map<String, Object>> attachMeta = new ArrayList<>();

        if (hasAttach) {
            List<ChatAttachmentDO> attachments = attachmentService.listByIds(dto.getAttachmentIds());
            for (ChatAttachmentDO a : attachments) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", a.getId());
                m.put("kind", a.getKind());
                m.put("fileName", a.getFileName());
                m.put("fileSize", a.getFileSize());
                m.put("mimeType", a.getMimeType());
                attachMeta.add(m);

                if ("image".equals(a.getKind())) {
                    String b64 = java.util.Base64.getEncoder().encodeToString(a.getData());
                    blocks.add(ImageBlock.builder()
                            .source(Base64Source.builder()
                                    .mediaType(a.getMimeType() != null ? a.getMimeType() : "application/octet-stream")
                                    .data(b64)
                                    .build())
                            .build());
                } else if (a.getParsedText() != null && !a.getParsedText().isBlank()) {
                    String text = a.getParsedText();
                    // token 预算内截断（保留前缀标记让模型识别这是附件内容）
                    if (attachmentMaxTokens > 0) {
                        int budget = attachmentMaxTokens;
                        int tokens = TokenCounter.estimate(text);
                        if (tokens > budget) {
                            text = trimToTokenBudget(text, budget);
                        }
                    }
                    blocks.add(TextBlock.builder().text("【附件：" + a.getFileName() + "】\n" + text).build());
                }
            }
        }

        if (hasText) {
            blocks.add(TextBlock.builder().text(dto.getMessage()).build());
        } else if (blocks.isEmpty()) {
            // 防御：至少有一个内容块，否则模型调用会失败（不应到达，因为上面 hasText||hasAttach 保证）
            blocks.add(TextBlock.builder().text("").build());
        }

        String attachmentsJson;
        try {
            attachmentsJson = attachMeta.isEmpty() ? null : objectMapper.writeValueAsString(attachMeta);
        } catch (Exception e) {
            attachmentsJson = null;
        }
        return new UserContent(blocks, attachmentsJson);
    }

    /**
     * 按 token 预算裁剪文本（近似：字符数 * 4/3 估算 token 数，简单线性截断）。
     * TokenCounter.estimate 按字符长度估算，此处用相同估算反推字符上限。
     */
    private String trimToTokenBudget(String text, int tokenBudget) {
        int estimateTotal = TokenCounter.estimate(text);
        if (estimateTotal <= tokenBudget) return text;
        int charBudget = Math.max(100, text.length() * tokenBudget / Math.max(1, estimateTotal));
        return text.substring(0, Math.min(text.length(), charBudget));
    }

    /**
     * 构建用户消息的返回结构：内容块 + 附件元信息 JSON（用于落库 chat_message.attachments）。
     */
    private record UserContent(List<ContentBlock> blocks, String attachmentsJson) {}

    /**
     * 未配置任何可用模型时提前失败（6018）。
     *
     * 背景：Agent 未绑定模型且全局 OPENAI_API_KEY 未配置时，此前会静默回退到占位配置
     * （api.openai.com + sk-placeholder），对话时报 "Remote host terminated the handshake"
     * 之类的晦涩网络错误，用户无法定位原因。
     * 校验顺序与 buildReActAgent 的解析优先级一致：chatModelId → 直填 apiKey → 全局配置。
     */
    private void ensureModelConfigured(AgentDO agentDO) {
        ModelConfig modelConfig = parseModelConfig(agentDO.getModelConfig());
        if (modelConfig.getChatModelId() != null && !modelConfig.getChatModelId().isBlank()) {
            // 已绑定 model 表模型，供应商存在性/权限由 buildReActAgent 校验（6002/6011/6012）
            return;
        }
        if (modelConfig.getApiKey() != null && !modelConfig.getApiKey().isBlank()) {
            return; // 直填配置
        }
        String globalKey = agentScopeConfig.getOpenai().getApiKey();
        if (globalKey != null && !globalKey.isBlank() && !"sk-placeholder".equals(globalKey)) {
            return; // 已配置全局默认模型（OPENAI_API_KEY）
        }
        throw new BusinessException(6018,
                "当前 Agent 未配置 Chat 模型：请先在 Agent「模型策略」中选择 Chat 模型（系统也未配置全局默认模型）");
    }

    /**
     * 构建 ReActAgent（每请求一个实例，非线程安全）
     */
    private ReActAgent buildReActAgent(AgentDO agentDO, String systemPrompt, String sessionId,
                                       List<Map<String, Object>> toolCallSink) {
        ModelConfig modelConfig = parseModelConfig(agentDO.getModelConfig());

        // 解析模型连接参数：优先 chatModelId（model 表 → provider 表），否则用 modelConfig 里的直填值，
        // 两者都为空时 GenerateOptions.mergeOptions 会回退到全局 agentscope.openai 配置
        String modelName = modelConfig.getModel();
        String apiKey = modelConfig.getApiKey();
        String baseUrl = modelConfig.getBaseUrl();

        if (modelConfig.getChatModelId() != null && !modelConfig.getChatModelId().isBlank()) {
            ModelDO chatModel = modelMapper.selectById(modelConfig.getChatModelId());
            if (chatModel == null) {
                throw new BusinessException(6002,
                        "Agent 绑定的 Chat 模型不存在（可能已被删除），请在「模型策略」中重新选择");
            }
            ModelProviderDO provider = modelProviderMapper.selectById(chatModel.getProviderId());
            if (provider == null) {
                throw new BusinessException(6011,
                        "Chat 模型关联的服务商配置不存在，请检查「模型管理」中的服务商设置");
            }
            // S4: 跨租户防护——Agent 只能使用所属工作空间的模型 / 供应商，
            // 否则可引用其他空间的模型，消耗他人供应商 API Key（横向越权 + 费用转嫁）。
            // 使用 null-safe 比较：当前 workspaceId 可能为空，直接 .equals 会 NPE（Bug1）
            if (!Objects.equals(RuntimeContext.getWorkspaceId(), provider.getWorkspaceId())) {
                throw new BusinessException(6012,
                        "当前工作空间无权使用该模型供应商，请选择本空间的模型");
            }
            modelName = chatModel.getModelId();
            apiKey = provider.getApiKey();
            baseUrl = provider.getBaseUrl();
        }

        // 根据 Agent 的 modelConfig 构建 GenerateOptions（覆盖全局默认值）
        GenerateOptions.Builder optionsBuilder = GenerateOptions.builder()
                .modelName(modelName)
                .temperature(modelConfig.getTemperature())
                .topP(modelConfig.getTopP())
                .maxTokens(modelConfig.getMaxTokens())
                .frequencyPenalty(modelConfig.getFrequencyPenalty())
                .stream(modelConfig.getStream())
                .apiKey(apiKey)
                .baseUrl(baseUrl);

        // 启用模型思考/推理输出（Qwen3 等模型需要 enable_thinking 参数才会返回 reasoning_content）
        // 通过 additionalBodyParam 传递，SDK 会将其平铺到请求体顶层（@JsonAnyGetter）
        optionsBuilder.additionalBodyParam("enable_thinking", true);

        GenerateOptions generateOptions = optionsBuilder.build();

        // Phase 2：Skill 注册为 AgentScope 原生工具（替换 MVP 的提示词注入）
        Toolkit toolkit = buildToolkit(agentDO.getId(), toolCallSink);

        return ReActAgent.builder()
                .name(agentDO.getName())
                .sysPrompt(systemPrompt)
                .model(model)
                .generateOptions(generateOptions)
                .maxIters(maxIters)
                .defaultSessionId(sessionId)
                .toolkit(toolkit)
                .build();
    }

    /**
     * 构建 Toolkit：把 Agent 已启用的 Skill 绑定注册为 AgentScope 工具，
     * LLM 通过 function calling 真实调用；chunk callback 收集本轮调用记录，
     * 最终写入 chat_message.skill_calls。
     */
    private Toolkit buildToolkit(String agentId, List<Map<String, Object>> toolCallSink) {
        Toolkit toolkit = new Toolkit();
        try {
            List<AgentSkillBindingVO> bindings = agentSkillService.listBindings(agentId);
            for (AgentSkillBindingVO binding : bindings) {
                if (!Boolean.TRUE.equals(binding.getEnabled())) {
                    continue;
                }
                skillRegistry.getExecutor(binding.getSkillId()).ifPresent(executor ->
                        toolkit.registerAgentTool(new SkillAgentTool(executor, skillCallLogRecorder)));
            }
        } catch (Exception e) {
            log.warn("加载 Agent Skill 列表失败: agentId={}, error={}", agentId, e.getMessage());
        }
        toolkit.setChunkCallback((use, result) -> {
            Map<String, Object> entry = new java.util.HashMap<>();
            entry.put("name", use.getName());
            entry.put("input", use.getInput());
            toolCallSink.add(entry);
        });
        return toolkit;
    }

    private String serializeToolCalls(List<Map<String, Object>> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(toolCalls);
        } catch (Exception e) {
            return null;
        }
    }

    private AgentDO loadAgent(String agentId) {
        AgentDO agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND);
        }
        // published、testing、draft、pending_review 状态可以对话（审批中仍可在控制台验证；
        // IM 回调侧由 ImBotServiceImpl 单独限定仅 PUBLISHED）
        if (agent.getStatus() != AgentStatus.PUBLISHED
                && agent.getStatus() != AgentStatus.TESTING
                && agent.getStatus() != AgentStatus.DRAFT
                && agent.getStatus() != AgentStatus.PENDING_REVIEW) {
            throw new BusinessException(3003, "该 Agent 已停用，无法对话");
        }
        return agent;
    }

    private ChatSessionDO getOrCreateSession(String sessionId, AgentDO agent) {
        if (sessionId != null && !sessionId.isBlank()) {
            ChatSessionDO session = sessionMapper.selectById(sessionId);
            if (session != null) {
                // 校验会话归属权（防止越权访问）
                String currentUserId = RuntimeContext.getUserId();
                if (!session.getUserId().equals(currentUserId)) {
                    throw new BusinessException(4003, "无权访问该会话");
                }
                // 跨 Agent 会话复用防护：session 必须属于当前 Agent，
                // 否则会注入另一 Agent 的历史/RAG 上下文（Bug4）
                if (agent.getId().equals(session.getAgentId())) {
                    return session;
                }
                // agentId 不匹配：不返回旧 session，下方创建新 session
            }
        }
        ChatSessionDO session = new ChatSessionDO();
        session.setAgentId(agent.getId());
        session.setUserId(RuntimeContext.getUserId());
        session.setTitle("新对话");
        session.setTokenCount(0L);
        session.setMessageCount(0);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    private String buildSystemPrompt(AgentDO agent, String sessionId) {
        StringBuilder sb = new StringBuilder();

        // 1. AGENTS.md 系统提示词
        String agentsMd = agent.getAgentsMd();
        if (agentsMd != null && !agentsMd.isBlank()) {
            sb.append(variableInjector.inject(agentsMd));
        } else {
            sb.append("You are a helpful assistant.");
        }

        // 2. 加载知识库 RAG 上下文（Skill 已改为 Toolkit 原生工具注册，不再注入提示词）
        String ragPrompt = buildRagContext(agent.getId(), sessionId);
        if (!ragPrompt.isEmpty()) {
            sb.append("\n\n").append(ragPrompt);
        }

        // 4. 加载历史上下文（根据 MemoryConfig 策略）
        MemoryConfig memoryConfig = parseMemoryConfig(agent.getMemoryConfig());
        int maxRounds = memoryConfig.getShortTermRounds() != null ? memoryConfig.getShortTermRounds() : 10;
        String overflowStrategy = memoryConfig.getOverflowStrategy() != null ? memoryConfig.getOverflowStrategy() : "sliding_window";
        // 排除本轮刚落库的当前用户消息，避免重复发送并腾出滑动窗口槽位（Bug1）
        // maxTokenWindow 作为 token 预算的二次裁剪上限（Bug7）
        int maxTokenWindow = memoryConfig.getMaxTokenWindow() != null ? memoryConfig.getMaxTokenWindow() : 0;
        String contextPrompt = memoryService.buildContextPrompt(sessionId, maxRounds, overflowStrategy, true,
                maxTokenWindow);

        if (!contextPrompt.isEmpty()) {
            sb.append("\n\n").append(contextPrompt);
        }

        return sb.toString();
    }

    /** RAG 上下文最大 token 预算（值由 agentone.chat.rag-max-tokens 注入，见上方字段） */

    /**
     * 构建 RAG 上下文
     *
     * 流程：
     * 1. 从所有绑定的知识库检索结果
     * 2. 按各 binding 的 similarityThreshold 过滤
     * 3. 全局按 score 降序排序（跨知识库统一排名）
     * 4. 在 token 预算内截取最相关的 chunk
     */
    private String buildRagContext(String agentId, String sessionId) {
        try {
            List<AgentKnowledgeBindingVO> bindings = knowledgeService.listBindings(agentId);
            if (bindings.isEmpty()) {
                return "";
            }

            String query = getLastUserMessage(sessionId);
            if (query == null || query.isBlank()) {
                return "";
            }

            // 1. 从所有知识库收集结果，各自 binding 的 threshold 过滤下沉到知识服务
            List<SearchResultVO> allResults = new java.util.ArrayList<>();
            for (AgentKnowledgeBindingVO binding : bindings) {
                int topK = binding.getTopK() != null ? binding.getTopK() : 5;
                List<SearchResultVO> results = knowledgeService.search(
                        binding.getKnowledgeId(), query, topK, binding.getSimilarityThreshold());
                allResults.addAll(results);
            }

            if (allResults.isEmpty()) {
                return "";
            }

            // 2. 全局按 score 降序排序
            allResults.sort((a, b) -> {
                double sa = a.getScore() != null ? a.getScore() : 0;
                double sb2 = b.getScore() != null ? b.getScore() : 0;
                return Double.compare(sb2, sa);
            });

            // 3. 在 token 预算内截取
            StringBuilder sb = new StringBuilder();
            sb.append("## 参考知识\n\n");
            sb.append("以下是从知识库中检索到的相关信息。请优先基于这些内容回答用户问题。\n");
            sb.append("如果参考知识不足以回答问题，请明确说明，不要编造信息。\n");
            sb.append("引用时请注明来源文档名称。\n\n");

            int tokenCount = 0;
            int chunkCount = 0;
            for (SearchResultVO result : allResults) {
                int chunkTokens = TokenCounter.estimate(result.getContent());
                if (tokenCount + chunkTokens > ragMaxTokens) {
                    break;
                }
                sb.append("### [").append(result.getDocumentName()).append("]\n");
                sb.append(result.getContent()).append("\n\n");
                tokenCount += chunkTokens;
                chunkCount++;
            }

            log.debug("RAG 上下文构建完成: agentId={}, 总结果={}, 采纳={}, tokens={}",
                    agentId, allResults.size(), chunkCount, tokenCount);

            return chunkCount > 0 ? sb.toString() : "";
        } catch (Exception e) {
            log.warn("RAG 上下文加载失败: agentId={}, error={}", agentId, e.getMessage());
            return "";
        }
    }

    /**
     * 获取会话中用户最近一条消息
     */
    private String getLastUserMessage(String sessionId) {
        List<ChatMessageDO> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageDO>()
                        .eq(ChatMessageDO::getSessionId, sessionId)
                        .eq(ChatMessageDO::getRole, "user")
                        .orderByDesc(ChatMessageDO::getCreatedAt)
                        .last("LIMIT 1")
        );
        return messages.isEmpty() ? null : messages.get(0).getContent();
    }

    private void saveMessage(String sessionId, String role, String content) {
        saveMessage(sessionId, role, content, null, null, null);
    }

    private void saveMessage(String sessionId, String role, String content, String skillCallsJson) {
        saveMessage(sessionId, role, content, skillCallsJson, null, null);
    }

    private void saveMessage(String sessionId, String role, String content, String skillCallsJson,
                             Integer durationMs) {
        saveMessage(sessionId, role, content, skillCallsJson, null, durationMs);
    }

    /**
     * 保存用户消息（含附件元信息 JSON）。独立命名避免与 4 参 skillCalls 重载签名冲突。
     */
    private void saveUserMessage(String sessionId, String content, String attachmentsJson) {
        ChatMessageDO msg = new ChatMessageDO();
        msg.setSessionId(sessionId);
        msg.setRole("user");
        msg.setContent(content);
        msg.setTokenCount(estimateTokens(content));
        msg.setSkillCalls(null);
        msg.setAttachments(attachmentsJson);
        msg.setDurationMs(null);
        msg.setTraceId(UUID.randomUUID().toString());
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    private void saveMessage(String sessionId, String role, String content, String skillCallsJson,
                             String attachmentsJson, Integer durationMs) {
        ChatMessageDO msg = new ChatMessageDO();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setTokenCount(estimateTokens(content));
        msg.setSkillCalls(skillCallsJson);
        msg.setAttachments(attachmentsJson);
        msg.setDurationMs(durationMs);
        msg.setTraceId(UUID.randomUUID().toString());
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    /**
     * 原子自增会话统计（并发安全，避免丢更新）。
     * message_count +2（本轮 1 条 user + 1 条 assistant），token_count 累加增量。
     */
    private void incrementSessionStats(String sessionId, int tokenDelta) {
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ChatSessionDO> statUpd =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        statUpd.eq(ChatSessionDO::getId, sessionId)
                .setSql("message_count = message_count + 2")
                .setSql("token_count = token_count + " + tokenDelta)
                .set(ChatSessionDO::getUpdatedAt, LocalDateTime.now());
        sessionMapper.update(null, statUpd);
    }

    /**
     * 模型/引擎调用失败时的补偿：保存一条 assistant 错误占位消息，保持 user/assistant 配对，
     * 并原子更新统计，避免留下孤立的 user 消息（Bug5）。
     */
    private void saveAssistantError(String sessionId, String userId, String workspaceId, String content) {
        try {
            com.agentone.common.context.Context ctx =
                    com.agentone.common.context.Context.of(userId, workspaceId);
            RuntimeContext.set(ctx);
            saveMessage(sessionId, "assistant", content);
            incrementSessionStats(sessionId, estimateTokens(content));
        } catch (Exception ignore) {
            log.warn("异常补偿消息落库失败: sessionId={}, error={}", sessionId, ignore.getMessage());
        } finally {
            RuntimeContext.clear();
        }
    }

    /** 取异常信息，脱敏截断，避免内部细节（堆栈/敏感路径）落入数据库或外泄（Bug9） */
    private String safeMsg(Throwable t) {
        String m = t != null ? t.getMessage() : null;
        if (m == null || m.isBlank()) {
            m = t != null && t.getClass().getSimpleName() != null
                    ? t.getClass().getSimpleName() : "未知错误";
        }
        // 去除换行/制表，剥离可能的堆栈片段，并截断到 200 字符
        String sanitized = m.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200) + "...";
        }
        return sanitized;
    }

    /**
     * 从 chat_message.attachments JSONB 中提取 attachment id 列表（容错：解析失败返回空）
     */
    private List<String> parseAttachmentIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = objectMapper.readValue(json, List.class);
            if (list == null) return List.of();
            List<String> ids = new ArrayList<>();
            for (Map<String, Object> m : list) {
                Object id = m.get("id");
                if (id instanceof String s && !s.isBlank()) ids.add(s);
            }
            return ids;
        } catch (Exception e) {
            log.warn("解析 attachments JSON 失败: {}", e.getMessage());
            return List.of();
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private ModelConfig parseModelConfig(String json) {
        try {
            if (json != null && !json.isBlank() && !"{}".equals(json)) {
                return objectMapper.readValue(json, ModelConfig.class);
            }
        } catch (Exception e) {
            log.warn("解析 modelConfig 失败: {}", e.getMessage());
        }
        return new ModelConfig();
    }

    private MemoryConfig parseMemoryConfig(String json) {
        try {
            if (json != null && !json.isBlank() && !"{}".equals(json)) {
                return objectMapper.readValue(json, MemoryConfig.class);
            }
        } catch (Exception e) {
            log.warn("解析 memoryConfig 失败: {}", e.getMessage());
        }
        return new MemoryConfig();
    }

    private ChatSessionVO toSessionVO(ChatSessionDO session) {
        ChatSessionVO vo = new ChatSessionVO();
        BeanUtils.copyProperties(session, vo);
        return vo;
    }

    private ChatMessageVO toMessageVO(ChatMessageDO message) {
        ChatMessageVO vo = new ChatMessageVO();
        BeanUtils.copyProperties(message, vo);
        return vo;
    }

    private int estimateTokens(String text) {
        return TokenCounter.estimate(text);
    }
}
