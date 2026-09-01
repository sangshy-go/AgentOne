package com.agentone.api.service;

import com.agentone.agent.entity.AgentDO;
import com.agentone.agent.mapper.AgentMapper;
import com.agentone.agent.mapper.ChatSessionMapper;
import com.agentone.agent.vo.DailyChatStatVO;
import com.agentone.api.vo.DashboardVO;
import com.agentone.knowledge.mapper.KnowledgeBaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 仪表盘统计服务。
 * 放在 api 模块：它是唯一同时可见 agent / knowledge / skill 模块 Mapper 的模块。
 * 全部查询经租户拦截器自动限定当前工作空间。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int TREND_DAYS = 7;

    private final AgentMapper agentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final ChatSessionMapper chatSessionMapper;

    public DashboardVO getStats() {
        DashboardVO vo = new DashboardVO();
        vo.setAgentCount(agentMapper.selectCount(null));
        vo.setKnowledgeCount(knowledgeBaseMapper.selectCount(null));

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        vo.setTodayChatCount(chatSessionMapper.countSessionsSince(todayStart));

        LocalDateTime weekStart = LocalDate.now().minusDays(TREND_DAYS - 1L).atStartOfDay();
        vo.setActiveUsers7d(chatSessionMapper.countActiveUsersSince(weekStart));
        vo.setDailyChats(buildTrend(weekStart));

        Page<AgentDO> recent = agentMapper.selectPage(new Page<>(1, 5),
                new LambdaQueryWrapper<AgentDO>().orderByDesc(AgentDO::getUpdatedAt));
        vo.setRecentAgents(recent.getRecords().stream().map(a -> {
            DashboardVO.RecentAgent r = new DashboardVO.RecentAgent();
            r.setId(a.getId());
            r.setName(a.getName());
            r.setCategory(a.getCategory());
            r.setUpdatedAt(a.getUpdatedAt());
            return r;
        }).collect(Collectors.toList()));
        return vo;
    }

    /**
     * 近 7 日趋势：DB 聚合结果缺日补 0，保证恒返回 TREND_DAYS 条
     */
    private List<DailyChatStatVO> buildTrend(LocalDateTime weekStart) {
        Map<String, DailyChatStatVO> byDate = chatSessionMapper.countSessionsByDay(weekStart).stream()
                .collect(Collectors.toMap(DailyChatStatVO::getStatDate, Function.identity()));
        List<DailyChatStatVO> trend = new ArrayList<>();
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            String day = LocalDate.now().minusDays(i).format(DAY_FMT);
            DailyChatStatVO hit = byDate.get(day);
            DailyChatStatVO item = new DailyChatStatVO();
            item.setStatDate(day);
            item.setStatCount(hit != null ? hit.getStatCount() : 0L);
            trend.add(item);
        }
        return trend;
    }
}
