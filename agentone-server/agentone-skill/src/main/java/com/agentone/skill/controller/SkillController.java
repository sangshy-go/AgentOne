package com.agentone.skill.controller;

import com.agentone.common.context.RuntimeContext;
import com.agentone.common.result.Result;
import com.agentone.skill.core.SkillResult;
import com.agentone.skill.dto.BindSkillDTO;
import com.agentone.skill.dto.DebugPreviewDTO;
import com.agentone.skill.dto.DebugRunDTO;
import com.agentone.skill.dto.SkillDTO;
import com.agentone.skill.dto.SkillTestDTO;
import com.agentone.skill.service.AgentSkillService;
import com.agentone.skill.service.SkillDebugService;
import com.agentone.skill.service.SkillService;
import com.agentone.skill.vo.AgentSkillBindingVO;
import com.agentone.skill.vo.DebugPreviewVO;
import com.agentone.skill.vo.DebugRunVO;
import com.agentone.skill.vo.DebugTargetVO;
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
    private final SkillService skillService;
    private final SkillDebugService skillDebugService;

    /**
     * 创建 API 模式 Skill
     */
    @PostMapping
    public Result<SkillVO> create(@Valid @RequestBody SkillDTO dto) {
        return Result.ok(skillService.create(dto));
    }

    /**
     * 更新 API 模式 Skill
     */
    @PutMapping("/{skillId}")
    public Result<SkillVO> update(@PathVariable String skillId, @Valid @RequestBody SkillDTO dto) {
        return Result.ok(skillService.update(skillId, dto));
    }

    /**
     * 删除 API 模式 Skill（存在 Agent 绑定时拒绝）
     */
    @DeleteMapping("/{skillId}")
    public Result<Void> delete(@PathVariable String skillId) {
        skillService.delete(skillId);
        return Result.ok();
    }

    /**
     * 测试调用 Skill（Skill 中心调试用，返回真实执行结果）
     */
    @PostMapping("/{skillId}/test")
    public Result<SkillResult> test(@PathVariable String skillId, @RequestBody SkillTestDTO dto) {
        return Result.ok(skillService.test(skillId, dto != null ? dto.getParams() : null));
    }

    /**
     * 调试器第 1 步：列出当前工作空间可调试的 Skill（builtin/api/mcp）
     */
    @GetMapping("/debug/targets")
    public Result<List<DebugTargetVO>> debugTargets() {
        return Result.ok(skillDebugService.listTargets());
    }

    /**
     * 调试器第 2 步：参数预检 + 执行计划预览（不发起真实调用）
     */
    @PostMapping("/debug/preview")
    public Result<DebugPreviewVO> debugPreview(@Valid @RequestBody DebugPreviewDTO dto) {
        return Result.ok(skillDebugService.preview(dto));
    }

    /**
     * 调试器第 3 步：真实执行（上下文取当前登录态，写调试审计）
     */
    @PostMapping("/debug/run")
    public Result<DebugRunVO> debugRun(@Valid @RequestBody DebugRunDTO dto) {
        return Result.ok(skillDebugService.run(dto));
    }

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
