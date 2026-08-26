package com.agentone.agent.service.impl;

import com.agentone.agent.entity.AgentDO;
import com.agentone.agent.entity.ChatMessageDO;
import com.agentone.agent.entity.ChatSessionDO;
import com.agentone.agent.mapper.AgentMapper;
import com.agentone.agent.mapper.ChatMessageMapper;
import com.agentone.agent.mapper.ChatSessionMapper;
import com.agentone.agent.service.MonitorService;
import com.agentone.agent.vo.MonitorSessionVO;
import com.agentone.agent.vo.SessionTimelineVO;
import com.agentone.agent.vo.SkillCallVO;
import com.agentone.common.exception.BusinessException;
import com.agentone.common.result.PageResult;
import com.agentone.common.service.UserLookupService;
import com.agentone.skill.entity.SkillCallLogDO;
import com.agentone.skill.entity.SkillDO;
import com.agentone.skill.mapper.SkillCallLogMapper;
import com.agentone.skill.mapper.SkillMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import java.time.LocalDateTime;

/**
 * 监控查询服务实现。
 * 租户隔离：chat_session / skill_call_log 带 workspace_id 由拦截器自动注入；
 * chat_message 在租户白名单内，时间线查询前必须先加载会话（带租户过滤）验证归属。
 */
@Service
@RequiredArgsConstructor
public class MonitorServiceImpl implements MonitorService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final AgentMapper agentMapper;
    private final SkillCallLogMapper skillCallLogMapper;
    private final SkillMapper skillMapper;
    private final UserLookupService userLookupService;

    @Override
    public PageResult<MonitorSessionVO> listSessions(String agentId, String keyword,
                                                     Integer recentHours,
                                                     Integer current, Integer size) {
        LambdaQueryWrapper<ChatSessionDO> wrapper = new LambdaQueryWrapper<>();
        if (agentId != null && !agentId.isBlank()) {
            wrapper.eq(ChatSessionDO::getAgentId, agentId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(ChatSessionDO::getTitle, keyword);
        }
        if (recentHours != null && recentHours > 0) {
            wrapper.ge(ChatSessionDO::getCreatedAt, LocalDateTime.now().minusHours(recentHours));
        }
        wrapper.orderByDesc(ChatSessionDO::getCreatedAt);
        Page<ChatSessionDO> page = chatSessionMapper.selectPage(new Page<>(current, size), wrapper);

        Map<String, String> agentNames = agentNames(
                page.getRecords().stream().map(ChatSessionDO::getAgentId).collect(Collectors.toSet()));
        Map<String, String> userEmails = userEmails(
                page.getRecords().stream().map(ChatSessionDO::getUserId).collect(Collectors.toSet()));

        Page<MonitorSessionVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(s -> {
            MonitorSessionVO vo = new MonitorSessionVO();
            vo.setId(s.getId());
            vo.setTitle(s.getTitle());
            vo.setAgentId(s.getAgentId());
            vo.setAgentName(agentNames.getOrDefault(s.getAgentId(), ""));
            vo.setUserEmail(userEmails.getOrDefault(s.getUserId(), ""));
            vo.setMessageCount(s.getMessageCount());
            vo.setTokenCount(s.getTokenCount());
            vo.setCreatedAt(s.getCreatedAt());
            vo.setUpdatedAt(s.getUpdatedAt());
            return vo;
        }).collect(Collectors.toList()));
        return PageResult.of(voPage);
    }

    @Override
    public SessionTimelineVO getSessionTimeline(String sessionId) {
        // chat_message 无租户列：先查会话（租户拦截生效）验证归属，防跨租户读取消息
        ChatSessionDO session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(4004, "会话不存在");
        }

        List<ChatMessageDO> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageDO>()
                        .eq(ChatMessageDO::getSessionId, sessionId)
                        .orderByAsc(ChatMessageDO::getCreatedAt));
        List<SkillCallLogDO> calls = skillCallLogMapper.selectList(
                new LambdaQueryWrapper<SkillCallLogDO>()
                        .eq(SkillCallLogDO::getSessionId, sessionId)
                        .orderByAsc(SkillCallLogDO::getCreatedAt));
        Map<String, String> skillNames = skillNames(
                calls.stream().map(SkillCallLogDO::getSkillId).collect(Collectors.toSet()));

        List<SessionTimelineVO.TimelineItem> items = new ArrayList<>();
        for (ChatMessageDO m : messages) {
            SessionTimelineVO.TimelineItem item = new SessionTimelineVO.TimelineItem();
            item.setKind("message");
            item.setId(m.getId());
            item.setRole(m.getRole());
            item.setContent(m.getContent());
            item.setDurationMs(m.getDurationMs() != null ? m.getDurationMs().longValue() : null);
            item.setTokenCount(m.getTokenCount());
            item.setCreatedAt(m.getCreatedAt());
            items.add(item);
        }
        for (SkillCallLogDO c : calls) {
            SessionTimelineVO.TimelineItem item = new SessionTimelineVO.TimelineItem();
            item.setKind("skill_call");
            item.setId(c.getId());
            item.setSkillId(c.getSkillId());
            item.setSkillName(skillNames.getOrDefault(c.getSkillId(), c.getSkillId()));
            item.setStatus(c.getStatus());
            item.setErrorMessage(c.getErrorMessage());
            item.setDurationMs(c.getDurationMs());
            item.setTokenCount(c.getTokenCount());
            item.setCreatedAt(c.getCreatedAt());
            items.add(item);
        }
        items.sort(Comparator.comparing(SessionTimelineVO.TimelineItem::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));

        SessionTimelineVO vo = new SessionTimelineVO();
        vo.setSession(toSessionVO(session));
        vo.setItems(items);
        return vo;
    }

    @Override
    public PageResult<SkillCallVO> listSkillCalls(String agentId, String skillId, String status,
                                                  Integer recentHours,
                                                  Integer current, Integer size) {
        LambdaQueryWrapper<SkillCallLogDO> wrapper = new LambdaQueryWrapper<>();
        if (agentId != null && !agentId.isBlank()) {
            wrapper.eq(SkillCallLogDO::getAgentId, agentId);
        }
        if (skillId != null && !skillId.isBlank()) {
            wrapper.eq(SkillCallLogDO::getSkillId, skillId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SkillCallLogDO::getStatus, status);
        }
        if (recentHours != null && recentHours > 0) {
            wrapper.ge(SkillCallLogDO::getCreatedAt, LocalDateTime.now().minusHours(recentHours));
        }
        wrapper.orderByDesc(SkillCallLogDO::getCreatedAt);
        Page<SkillCallLogDO> page = skillCallLogMapper.selectPage(new Page<>(current, size), wrapper);

        Map<String, String> agentNames = agentNames(
                page.getRecords().stream().map(SkillCallLogDO::getAgentId).collect(Collectors.toSet()));
        Map<String, String> skillNames = skillNames(
                page.getRecords().stream().map(SkillCallLogDO::getSkillId).collect(Collectors.toSet()));

        Page<SkillCallVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(c -> {
            SkillCallVO vo = new SkillCallVO();
            vo.setId(c.getId());
            vo.setAgentId(c.getAgentId());
            vo.setAgentName(agentNames.getOrDefault(c.getAgentId(), ""));
            vo.setSkillId(c.getSkillId());
            vo.setSkillName(skillNames.getOrDefault(c.getSkillId(), c.getSkillId()));
            vo.setSessionId(c.getSessionId());
            vo.setStatus(c.getStatus());
            vo.setErrorMessage(c.getErrorMessage());
            vo.setDurationMs(c.getDurationMs());
            vo.setTokenCount(c.getTokenCount());
            vo.setCreatedAt(c.getCreatedAt());
            return vo;
        }).collect(Collectors.toList()));
        return PageResult.of(voPage);
    }

    private MonitorSessionVO toSessionVO(ChatSessionDO s) {
        MonitorSessionVO vo = new MonitorSessionVO();
        vo.setId(s.getId());
        vo.setTitle(s.getTitle());
        vo.setAgentId(s.getAgentId());
        vo.setAgentName(agentNames(Set.of(s.getAgentId())).getOrDefault(s.getAgentId(), ""));
        vo.setUserEmail(userEmails(Set.of(s.getUserId())).getOrDefault(s.getUserId(), ""));
        vo.setMessageCount(s.getMessageCount());
        vo.setTokenCount(s.getTokenCount());
        vo.setCreatedAt(s.getCreatedAt());
        vo.setUpdatedAt(s.getUpdatedAt());
        return vo;
    }

    private Map<String, String> agentNames(Collection<String> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return agentMapper.selectBatchIds(agentIds).stream()
                .collect(Collectors.toMap(AgentDO::getId, AgentDO::getName, (a, b) -> a));
    }

    private Map<String, String> skillNames(Collection<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // builtin Skill 不在 DB 中，调用方以 skillId 兜底显示
        return skillMapper.selectBatchIds(skillIds).stream()
                .collect(Collectors.toMap(SkillDO::getId, SkillDO::getName, (a, b) -> a));
    }

    private Map<String, String> userEmails(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userLookupService.findByIds(new ArrayList<>(userIds)).stream()
                .collect(Collectors.toMap(UserLookupService.UserInfo::userId,
                        UserLookupService.UserInfo::email, (a, b) -> a));
    }
}
