package com.agentone.agent.memory;

import com.agentone.agent.entity.ChatMessageDO;
import com.agentone.agent.mapper.ChatMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 记忆服务
 * 负责加载和压缩对话上下文
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final ChatMessageMapper messageMapper;

    /** 角色中文映射（避免每次 switch 匹配） */
    private static final Map<String, String> ROLE_LABELS = Map.of(
            "user", "用户",
            "assistant", "助手",
            "system", "系统",
            "tool", "工具"
    );

    /**
     * 加载会话的历史消息（滑动窗口）
     *
     * @param sessionId   会话 ID
     * @param maxRounds   最大保留轮数（1轮 = 1条用户消息 + 1条助手回复）
     * @return 历史消息列表（按时间正序）
     */
    public List<ChatMessageDO> loadHistory(String sessionId, int maxRounds) {
        return loadHistory(sessionId, maxRounds, false);
    }

    /**
     * 加载会话的历史消息（滑动窗口）
     *
     * @param sessionId                   会话 ID
     * @param maxRounds                   最大保留轮数（1轮 = 1条用户消息 + 1条助手回复）
     * @param excludeLatestUserMessage   若为 true，则排除时间最新的一条 user 消息
     *                                   （即本轮刚落库、即将作为当前请求发出的那条用户消息，
     *                                   避免它既出现在历史上下文里、又作为当前消息重复发送，
     *                                   同时避免滑动窗口被它占掉一个真实历史槽位）
     * @return 历史消息列表（按时间正序）
     */
    public List<ChatMessageDO> loadHistory(String sessionId, int maxRounds, boolean excludeLatestUserMessage) {
        if (maxRounds <= 0) {
            return Collections.emptyList();
        }

        // 若需排除当前用户消息，多取 1 条，保证窗口仍保留 maxRounds 轮的"真实"历史
        int maxMessages = maxRounds * 2 + (excludeLatestUserMessage ? 1 : 0);

        // SQL 层直接 LIMIT，避免全量加载到内存
        List<ChatMessageDO> latestDesc = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageDO>()
                        .eq(ChatMessageDO::getSessionId, sessionId)
                        .orderByDesc(ChatMessageDO::getCreatedAt)
                        .last("LIMIT " + maxMessages)
        );

        if (latestDesc.isEmpty()) {
            return Collections.emptyList();
        }

        // 排除最新的一条 user 消息（即本轮刚保存的当前用户消息）
        if (excludeLatestUserMessage
                && !latestDesc.isEmpty()
                && "user".equals(latestDesc.get(0).getRole())) {
            latestDesc.remove(0);
        }

        if (latestDesc.isEmpty()) {
            return Collections.emptyList();
        }

        // 反转为正序（最新的在最后）
        List<ChatMessageDO> result = new ArrayList<>(latestDesc);
        Collections.reverse(result);
        return result;
    }

    /**
     * 构建上下文 Prompt（用于发送给 LLM）
     * 支持 overflowStrategy：sliding_window（默认）/ summary（待实现）
     * 
     *  "以下是之前的对话记录：

        [用户]: xxx
        [助手]: xxx
        ..."
     *
     * @param sessionId        会话 ID
     * @param maxRounds        最大保留轮数
     * @param overflowStrategy Token 超限策略（当前仅支持 sliding_window）
     * @return 格式化的上下文文本
     */
    public String buildContextPrompt(String sessionId, int maxRounds, String overflowStrategy) {
        return buildContextPrompt(sessionId, maxRounds, overflowStrategy, false, 0);
    }

    /**
     * 构建上下文 Prompt（用于发送给 LLM）
     *
     * @param excludeLatestUserMessage 若为 true，则历史中排除本轮刚落库的当前用户消息
     *                                 （避免当前问题既出现在历史里、又作为当前消息重复发送）
     * @param maxTokenWindow           历史总 token 预算上限（0 或负数表示不限制）；
     *                                 在 count 滑动窗口基础上做二次裁剪，防止长历史超出上下文窗口（Bug7）
     */
    public String buildContextPrompt(String sessionId, int maxRounds, String overflowStrategy,
                                      boolean excludeLatestUserMessage, int maxTokenWindow) {
        // TODO: 实现 summary 策略（当 Token 超限时，用 LLM 总结历史）
        if ("summary".equals(overflowStrategy)) {
            log.debug("summary 策略暂未实现，回退到 sliding_window");
        }

        List<ChatMessageDO> history = loadHistory(sessionId, maxRounds, excludeLatestUserMessage);

        // 二次裁剪：按 token 预算收敛，优先保留最新的消息（Bug7）
        if (maxTokenWindow > 0) {
            history = trimToTokenBudget(history, maxTokenWindow);
        }

        if (history.isEmpty()) {
            return "";
        }

        StringJoiner joiner = new StringJoiner("\n\n", "以下是之前的对话记录：\n\n", "");
        for (ChatMessageDO msg : history) {
            String role = ROLE_LABELS.getOrDefault(msg.getRole(), msg.getRole());
            joiner.add("[" + role + "]: " + msg.getContent());
        }
        return joiner.toString();
    }

    /**
     * 在已有 count 滑动窗口结果上，按 token 总量做二次裁剪（从最新消息向前累加，
     * 超过 maxTokenWindow 即停止，从而保留最近的对话，丢弃最旧的部分）。
     */
    private List<ChatMessageDO> trimToTokenBudget(List<ChatMessageDO> history, int maxTokenWindow) {
        int total = 0;
        List<ChatMessageDO> kept = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            int t = history.get(i).getTokenCount() != null ? history.get(i).getTokenCount() : 0;
            if (!kept.isEmpty() && total + t > maxTokenWindow) {
                break;
            }
            kept.add(0, history.get(i));
            total += t;
        }
        return kept;
    }

    /**
     * 构建上下文 Prompt（向后兼容方法）
     */
    public String buildContextPrompt(String sessionId, int maxRounds) {
        return buildContextPrompt(sessionId, maxRounds, "sliding_window");
    }
}
