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
        if (maxRounds <= 0) {
            return Collections.emptyList();
        }

        int maxMessages = maxRounds * 2;  // 每轮 2 条消息

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
        // TODO: 实现 summary 策略（当 Token 超限时，用 LLM 总结历史）
        if ("summary".equals(overflowStrategy)) {
            log.debug("summary 策略暂未实现，回退到 sliding_window");
        }

        List<ChatMessageDO> history = loadHistory(sessionId, maxRounds);

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
     * 构建上下文 Prompt（向后兼容方法）
     */
    public String buildContextPrompt(String sessionId, int maxRounds) {
        return buildContextPrompt(sessionId, maxRounds, "sliding_window");
    }
}
