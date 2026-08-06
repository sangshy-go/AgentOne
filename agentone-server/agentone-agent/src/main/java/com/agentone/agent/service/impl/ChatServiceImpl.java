package com.agentone.agent.service.impl;

import com.agentone.agent.dto.ChatRequestDTO;
import com.agentone.agent.entity.AgentDO;
import com.agentone.agent.entity.ChatMessageDO;
import com.agentone.agent.entity.ChatSessionDO;
import com.agentone.agent.enums.AgentStatus;
import com.agentone.agent.mapper.AgentMapper;
import com.agentone.agent.mapper.ChatMessageMapper;
import com.agentone.agent.mapper.ChatSessionMapper;
import com.agentone.agent.memory.MemoryService;
import com.agentone.agent.model.MemoryConfig;
import com.agentone.agent.model.ModelConfig;
import com.agentone.agent.persona.VariableInjector;
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
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
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
    private final KnowledgeService knowledgeService;
    private final ModelMapper modelMapper;
    private final ModelProviderMapper modelProviderMapper;
    private final Model model;  // AgentScope 自动配置的 Model Bean
    private final ObjectMapper objectMapper;

    /** Q6: ReAct 最大迭代次数（原硬编码 10），可通过 agentone.chat.max-iters 覆盖 */
    @org.springframework.beans.factory.annotation.Value("${agentone.chat.max-iters:10}")
    private int maxIters;

    /** Q6: RAG 上下文最大 token 预算（原硬编码 3000），可通过 agentone.chat.rag-max-tokens 覆盖 */
    @org.springframework.beans.factory.annotation.Value("${agentone.chat.rag-max-tokens:3000}")
    private int ragMaxTokens;

    @Override
    public ChatResponseVO chat(ChatRequestDTO dto) {
        long startTime = System.currentTimeMillis();

        // 1. 加载 Agent
        AgentDO agentDO = loadAgent(dto.getAgentId());

        // 2. 获取或创建会话
        ChatSessionDO session = getOrCreateSession(dto.getSessionId(), agentDO);

        // 3. 先保存用户消息（RAG 检索需要当前消息作为 query）
        saveMessage(session.getId(), "user", dto.getMessage());

        // 4. 构建系统提示词（AGENTS.md + 变量注入 + RAG + 历史上下文）
        String systemPrompt = buildSystemPrompt(agentDO, session.getId());

        // 5. 创建 ReActAgent（每请求一个实例）
        ReActAgent agent = buildReActAgent(agentDO, systemPrompt, session.getId());

        // 6. 构建 AgentScope RuntimeContext
        io.agentscope.core.agent.RuntimeContext agentCtx = io.agentscope.core.agent.RuntimeContext.builder()
                .sessionId(session.getId())
                .userId(RuntimeContext.getUserId())
                .put("workspace_id", RuntimeContext.getWorkspaceId())
                .put("agent_id", agentDO.getId())
                .build();

        // 7. 调用 AgentScope（同步）
        Msg userMsg = Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .content(List.of(TextBlock.builder().text(dto.getMessage()).build()))
                .build();

        Msg response = agent.call(List.of(userMsg), agentCtx).block();
        String reply = response != null ? response.getTextContent() : "";

        // 8. 保存助手回复
        saveMessage(session.getId(), "assistant", reply);

        int totalTokens = estimateTokens(dto.getMessage() + reply);

        // 9. 原子更新会话统计（避免并发对话同一 session 时 messageCount 自增丢更新）
        // 用 UpdateWrapper 在 DB 层做原子自增，而非先查再改
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ChatSessionDO> statUpd =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        statUpd.eq(ChatSessionDO::getId, session.getId())
                .setSql("message_count = message_count + 2")
                .setSql("token_count = token_count + " + totalTokens)
                .set(ChatSessionDO::getUpdatedAt, LocalDateTime.now());
        sessionMapper.update(null, statUpd);

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
        // 1. 加载 Agent + 会话
        AgentDO agentDO = loadAgent(dto.getAgentId());
        ChatSessionDO session = getOrCreateSession(dto.getSessionId(), agentDO);

        // 2. 捕获当前请求上下文（SSE doOnComplete 触发时 ThreadLocal 已被清除）
        String capturedUserId = RuntimeContext.getUserId();
        String capturedWorkspaceId = RuntimeContext.getWorkspaceId();

        // 3. 先保存用户消息（RAG 检索需要当前消息作为 query）
        saveMessage(session.getId(), "user", dto.getMessage());

        // 4. 构建上下文（AGENTS.md + RAG + 历史）
        String systemPrompt = buildSystemPrompt(agentDO, session.getId());
        ReActAgent agent = buildReActAgent(agentDO, systemPrompt, session.getId());

        io.agentscope.core.agent.RuntimeContext agentCtx = io.agentscope.core.agent.RuntimeContext.builder()
                .sessionId(session.getId())
                .userId(capturedUserId)
                .put("workspace_id", capturedWorkspaceId)
                .build();

        // 5. 构建用户消息
        Msg userMsg = Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .content(List.of(TextBlock.builder().text(dto.getMessage()).build()))
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
                    // 注意：此时 RuntimeContext 已被 JwtAuthFilter 清除
                    // 需要临时恢复，以便 MyBatis 自动填充 workspaceId
                    try {
                        com.agentone.common.context.Context ctx =
                                com.agentone.common.context.Context.of(capturedUserId, capturedWorkspaceId);
                        RuntimeContext.set(ctx);

                        saveMessage(session.getId(), "assistant", fullReply.toString());
                        // 原子自增 message_count（并发安全，避免丢更新）
                        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ChatSessionDO> statUpd =
                                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
                        statUpd.eq(ChatSessionDO::getId, session.getId())
                                .setSql("message_count = message_count + 2")
                                .set(ChatSessionDO::getUpdatedAt, LocalDateTime.now());
                        sessionMapper.update(null, statUpd);
                    } finally {
                        RuntimeContext.clear();
                    }
                })
                .doOnError(error -> {
                    // 流中途失败：记录错误日志，保存已接收的部分内容（如果有）
                    log.error("流式对话失败: sessionId={}, error={}", session.getId(), error.getMessage());
                    if (fullReply.length() > 0) {
                        try {
                            com.agentone.common.context.Context ctx =
                                    com.agentone.common.context.Context.of(capturedUserId, capturedWorkspaceId);
                            RuntimeContext.set(ctx);
                            saveMessage(session.getId(), "assistant",
                                    fullReply.toString() + "\n\n[错误: " + error.getMessage() + "]");
                        } finally {
                            RuntimeContext.clear();
                        }
                    }
                });

        // 结束事件：[DONE]
        ServerSentEvent<String> doneEvent = ServerSentEvent.<String>builder()
                .event("done")
                .data("[DONE]")
                .build();

        // 拼接：session → deltas → done，错误时追加 error 事件
        return Flux.concat(Flux.just(sessionEvent), deltaFlux, Flux.just(doneEvent))
                .onErrorResume(error -> Flux.just(ServerSentEvent.<String>builder()
                        .event("error")
                        .data(error.getMessage() != null ? error.getMessage() : "未知错误")
                        .build()));
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
        messageMapper.delete(
                new LambdaQueryWrapper<ChatMessageDO>()
                        .eq(ChatMessageDO::getSessionId, sessionId)
        );
        sessionMapper.deleteById(sessionId);
    }

    // ==================== 内部方法 ====================

    /**
     * 构建 ReActAgent（每请求一个实例，非线程安全）
     */
    private ReActAgent buildReActAgent(AgentDO agentDO, String systemPrompt, String sessionId) {
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
            if (!RuntimeContext.getWorkspaceId().equals(provider.getWorkspaceId())) {
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

        return ReActAgent.builder()
                .name(agentDO.getName())
                .sysPrompt(systemPrompt)
                .model(model)
                .generateOptions(generateOptions)
                .maxIters(maxIters)
                .defaultSessionId(sessionId)
                .build();
    }

    private AgentDO loadAgent(String agentId) {
        AgentDO agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND);
        }
        // 只有 published、testing、draft 状态可以对话
        if (agent.getStatus() != AgentStatus.PUBLISHED
                && agent.getStatus() != AgentStatus.TESTING
                && agent.getStatus() != AgentStatus.DRAFT) {
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
                return session;
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

        // 2. 加载 Agent 绑定的 Skill 列表
        String skillPrompt = buildSkillPrompt(agent.getId());
        if (!skillPrompt.isEmpty()) {
            sb.append("\n\n").append(skillPrompt);
        }

        // 3. 加载知识库 RAG 上下文
        String ragPrompt = buildRagContext(agent.getId(), sessionId);
        if (!ragPrompt.isEmpty()) {
            sb.append("\n\n").append(ragPrompt);
        }

        // 4. 加载历史上下文（根据 MemoryConfig 策略）
        MemoryConfig memoryConfig = parseMemoryConfig(agent.getMemoryConfig());
        int maxRounds = memoryConfig.getShortTermRounds() != null ? memoryConfig.getShortTermRounds() : 10;
        String overflowStrategy = memoryConfig.getOverflowStrategy() != null ? memoryConfig.getOverflowStrategy() : "sliding_window";
        String contextPrompt = memoryService.buildContextPrompt(sessionId, maxRounds, overflowStrategy);

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

            // 1. 从所有知识库收集结果，带各自 binding 的 threshold 过滤
            List<SearchResultVO> allResults = new java.util.ArrayList<>();
            for (AgentKnowledgeBindingVO binding : bindings) {
                int topK = binding.getTopK() != null ? binding.getTopK() : 5;
                List<SearchResultVO> results = knowledgeService.search(
                        binding.getKnowledgeId(), query, topK);

                for (SearchResultVO result : results) {
                    if (result.getScore() != null
                            && binding.getSimilarityThreshold() != null
                            && result.getScore() < binding.getSimilarityThreshold()) {
                        continue;
                    }
                    allResults.add(result);
                }
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

    /**
     * 构建 Skill 提示词
     * 将 Agent 绑定的 Skill 描述注入到系统提示词中，让 LLM 知道有哪些工具可用
     */
    private String buildSkillPrompt(String agentId) {
        try {
            List<AgentSkillBindingVO> bindings = agentSkillService.listBindings(agentId);
            if (bindings.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## 可用工具（Skill）\n\n");
            sb.append("你可以调用以下工具来完成任务：\n\n");

            for (AgentSkillBindingVO binding : bindings) {
                if (!Boolean.TRUE.equals(binding.getEnabled())) {
                    continue;
                }
                sb.append("- **").append(binding.getSkillName()).append("**（ID: ")
                  .append(binding.getSkillId()).append("）: ");

                // 从 Registry 获取描述
                skillRegistry.getExecutor(binding.getSkillId()).ifPresent(executor -> {
                    // 这里只是为了确认存在，描述在下一行输出
                });

                // 直接输出类型信息
                sb.append("类型: ").append(binding.getSkillType() != null ? binding.getSkillType() : "unknown");
                sb.append("\n");
            }

            sb.append("\n当需要使用某个工具时，请在回复中说明调用意图。");
            return sb.toString();
        } catch (Exception e) {
            log.warn("加载 Agent Skill 列表失败: agentId={}, error={}", agentId, e.getMessage());
            return "";
        }
    }

    private void saveMessage(String sessionId, String role, String content) {
        ChatMessageDO msg = new ChatMessageDO();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setTokenCount(estimateTokens(content));
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
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
