package com.agentone.skill.controller;

import com.agentone.common.context.RuntimeContext;
import com.agentone.common.result.Result;
import com.agentone.skill.dto.BindSkillDTO;
import com.agentone.skill.service.AgentSkillService;
import com.agentone.skill.vo.AgentSkillBindingVO;
import com.agentone.skill.vo.SkillVO;
import com.agentone.common.result.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Skill 管理 Controller
 */
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {

    private final AgentSkillService agentSkillService;

    /**
     * 获取工作空间的所有 Skill
     */
    @GetMapping
    public Result<PageResult<SkillVO>> listSkills(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        String workspaceId = RuntimeContext.getWorkspaceId();
        return Result.ok(agentSkillService.listSkills(workspaceId, page, size));
    }

    /**
     * 获取 Agent 绑定的 Skill 列表
     */
    @GetMapping("/bindings/{agentId}")
    public Result<List<AgentSkillBindingVO>> listBindings(@PathVariable String agentId) {
        return Result.ok(agentSkillService.listBindings(agentId));
    }

    
    /**
     * 绑定 Skill 到 Agent
     */
    @PostMapping("/bind")
    public Result<AgentSkillBindingVO> bind(@Valid @RequestBody BindSkillDTO dto) {
        return Result.ok(agentSkillService.bind(dto));
    }

    /**
     * 解绑 Skill
     */
    @DeleteMapping("/bindings/{bindingId}")
    public Result<Void> unbind(@PathVariable String bindingId) {
        agentSkillService.unbind(bindingId);
        return Result.ok();
    }

    /**
     * 启用/禁用绑定
     */
    @PutMapping("/bindings/{bindingId}/toggle")
    public Result<Void> toggleEnabled(@PathVariable String bindingId,
                                       @RequestParam boolean enabled) {
        agentSkillService.toggleEnabled(bindingId, enabled);
        return Result.ok();
    }
}
