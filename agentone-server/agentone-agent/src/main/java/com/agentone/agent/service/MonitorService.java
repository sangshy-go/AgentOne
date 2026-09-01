package com.agentone.agent.service;

import com.agentone.agent.vo.MonitorSessionVO;
import com.agentone.agent.vo.SessionTimelineVO;
import com.agentone.agent.vo.SkillCallVO;
import com.agentone.common.result.PageResult;

/**
 * 监控查询服务：全工作空间的会话日志 / 会话时间线 / Skill 调用记录
 */
public interface MonitorService {

    PageResult<MonitorSessionVO> listSessions(String agentId, String keyword, Integer recentHours,
                                              Integer current, Integer size);

    SessionTimelineVO getSessionTimeline(String sessionId);

    PageResult<SkillCallVO> listSkillCalls(String agentId, String skillId, String status,
                                           Integer recentHours, Integer current, Integer size);
}
