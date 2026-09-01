package com.agentone.api.vo;

import com.agentone.agent.vo.DailyChatStatVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控仪表盘统计数据（规格 §11.1 P0：核心指标 + 对话趋势）
 */
@Data
public class DashboardVO {

    private Long agentCount;
    private Long knowledgeCount;
    /** 今日对话数（会话数） */
    private Long todayChatCount;
    /** 近 7 日活跃用户数（去重） */
    private Long activeUsers7d;
    /** 近 7 日每日会话数（缺日补 0，恒为 7 条） */
    private List<DailyChatStatVO> dailyChats;
    /** 最近更新的前 5 个 Agent */
    private List<RecentAgent> recentAgents;

    @Data
    public static class RecentAgent {
        private String id;
        private String name;
        private String category;
        private LocalDateTime updatedAt;
    }
}
