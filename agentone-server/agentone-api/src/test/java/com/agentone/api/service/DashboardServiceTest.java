package com.agentone.api.service;

import com.agentone.agent.entity.AgentDO;
import com.agentone.agent.mapper.AgentMapper;
import com.agentone.agent.mapper.ChatSessionMapper;
import com.agentone.agent.vo.DailyChatStatVO;
import com.agentone.api.vo.DashboardVO;
import com.agentone.knowledge.mapper.KnowledgeBaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * DashboardService 单测（课题⑥）：计数透传 + 7 日趋势缺日补 0。
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Mock
    private AgentMapper agentMapper;
    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock
    private ChatSessionMapper chatSessionMapper;

    @InjectMocks
    private DashboardService service;

    private DailyChatStatVO stat(String date, long count) {
        DailyChatStatVO v = new DailyChatStatVO();
        v.setStatDate(date);
        v.setStatCount(count);
        return v;
    }

    @Test
    void getStats_countsPassThrough() {
        when(agentMapper.selectCount(isNull())).thenReturn(3L);
        when(knowledgeBaseMapper.selectCount(isNull())).thenReturn(2L);
        when(chatSessionMapper.countSessionsSince(any(LocalDateTime.class))).thenReturn(7L);
        when(chatSessionMapper.countActiveUsersSince(any(LocalDateTime.class))).thenReturn(4L);
        when(chatSessionMapper.countSessionsByDay(any(LocalDateTime.class))).thenReturn(List.of());
        when(agentMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 5));

        DashboardVO vo = service.getStats();
        assertEquals(3L, vo.getAgentCount());
        assertEquals(2L, vo.getKnowledgeCount());
        assertEquals(7L, vo.getTodayChatCount());
        assertEquals(4L, vo.getActiveUsers7d());
    }

    @Test
    void getStats_trendAlways7Days_missingDaysFilledWithZero() {
        when(agentMapper.selectCount(isNull())).thenReturn(0L);
        when(knowledgeBaseMapper.selectCount(isNull())).thenReturn(0L);
        when(chatSessionMapper.countSessionsSince(any(LocalDateTime.class))).thenReturn(0L);
        when(chatSessionMapper.countActiveUsersSince(any(LocalDateTime.class))).thenReturn(0L);

        String today = LocalDate.now().format(DAY_FMT);
        String threeDaysAgo = LocalDate.now().minusDays(3).format(DAY_FMT);
        when(chatSessionMapper.countSessionsByDay(any(LocalDateTime.class)))
                .thenReturn(List.of(stat(today, 5L), stat(threeDaysAgo, 2L)));
        when(agentMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 5));

        DashboardVO vo = service.getStats();
        assertEquals(7, vo.getDailyChats().size(), "趋势必须恒为 7 天");
        // 升序排列：最后一条是今天
        assertEquals(today, vo.getDailyChats().get(6).getStatDate());
        assertEquals(5L, vo.getDailyChats().get(6).getStatCount());
        // 缺日补 0
        assertEquals(2L, vo.getDailyChats().get(3).getStatCount());
        assertEquals(0L, vo.getDailyChats().get(0).getStatCount());
        assertEquals(0L, vo.getDailyChats().get(5).getStatCount());
    }

    @Test
    void getStats_recentAgentsMapped() {
        when(agentMapper.selectCount(isNull())).thenReturn(0L);
        when(knowledgeBaseMapper.selectCount(isNull())).thenReturn(0L);
        when(chatSessionMapper.countSessionsSince(any(LocalDateTime.class))).thenReturn(0L);
        when(chatSessionMapper.countActiveUsersSince(any(LocalDateTime.class))).thenReturn(0L);
        when(chatSessionMapper.countSessionsByDay(any(LocalDateTime.class))).thenReturn(List.of());

        AgentDO agent = new AgentDO();
        agent.setId("agent-1");
        agent.setName("客服助手");
        agent.setCategory("客服");
        agent.setUpdatedAt(LocalDateTime.now());
        Page<AgentDO> page = new Page<>(1, 5);
        page.setRecords(List.of(agent));
        when(agentMapper.selectPage(any(), any())).thenReturn(page);

        DashboardVO vo = service.getStats();
        assertEquals(1, vo.getRecentAgents().size());
        assertEquals("agent-1", vo.getRecentAgents().get(0).getId());
        assertEquals("客服助手", vo.getRecentAgents().get(0).getName());
        assertEquals("客服", vo.getRecentAgents().get(0).getCategory());
    }
}
