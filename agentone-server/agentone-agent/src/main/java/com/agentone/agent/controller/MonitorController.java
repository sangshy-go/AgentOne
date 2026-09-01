package com.agentone.agent.controller;

import com.agentone.agent.service.MonitorService;
import com.agentone.agent.vo.MonitorSessionVO;
import com.agentone.agent.vo.SessionTimelineVO;
import com.agentone.agent.vo.SkillCallVO;
import com.agentone.common.result.PageResult;
import com.agentone.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监控日志：全工作空间的对话会话、会话时间线（Skill 调用链）、Skill 调用记录
 */
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;

    @GetMapping("/sessions")
    public Result<PageResult<MonitorSessionVO>> sessions(
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer recentHours,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(monitorService.listSessions(agentId, keyword, recentHours, current, size));
    }

    @GetMapping("/sessions/{id}/timeline")
    public Result<SessionTimelineVO> timeline(@PathVariable String id) {
        return Result.ok(monitorService.getSessionTimeline(id));
    }

    @GetMapping("/skill-calls")
    public Result<PageResult<SkillCallVO>> skillCalls(
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String skillId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer recentHours,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(monitorService.listSkillCalls(agentId, skillId, status, recentHours, current, size));
    }
}
