package com.agentone.skill.controller;

import com.agentone.common.context.RuntimeContext;
import com.agentone.common.result.Result;
import com.agentone.skill.core.SkillResult;
import com.agentone.skill.dto.BindSkillDTO;
import com.agentone.skill.dto.DebugPreviewDTO;
import com.agentone.skill.dto.DebugRunDTO;
import com.agentone.skill.dto.InvokeSkillDTO;
import com.agentone.skill.dto.SkillDTO;
import com.agentone.skill.dto.SkillImportDTO;
import com.agentone.skill.dto.SkillTestDTO;
import com.agentone.skill.service.AgentSkillService;
import com.agentone.skill.service.SkillDebugService;
import com.agentone.skill.service.SkillService;
import com.agentone.skill.vo.AgentSkillBindingVO;
import com.agentone.skill.vo.DebugPreviewVO;
import com.agentone.skill.vo.DebugRunVO;
import com.agentone.skill.vo.DebugTargetVO;
import com.agentone.skill.vo.InvokeSkillVO;
import com.agentone.skill.vo.SkillExportVO;
import com.agentone.skill.vo.SkillPackageFileVO;
import com.agentone.skill.vo.SkillVO;
import com.agentone.common.result.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
     * 创建用户 Skill（type: api=HTTP 封装（缺省）/ prompt=内容型指令）
     */
    @PostMapping
    public Result<SkillVO> create(@Valid @RequestBody SkillDTO dto) {
        return Result.ok(skillService.create(dto));
    }

    /**
     * 更新用户 Skill（类型不可变更）
     */
    @PutMapping("/{skillId}")
    public Result<SkillVO> update(@PathVariable String skillId, @Valid @RequestBody SkillDTO dto) {
        return Result.ok(skillService.update(skillId, dto));
    }

    /**
     * 删除用户 Skill（存在 Agent 绑定时拒绝）
     */
    @DeleteMapping("/{skillId}")
    public Result<Void> delete(@PathVariable String skillId) {
        skillService.delete(skillId);
        return Result.ok();
    }

    /**
     * 导出用户 Skill 为 JSON 定义（发布/分享的最小形态）
     */
    @GetMapping("/{skillId}/export")
    public Result<SkillExportVO> export(@PathVariable String skillId) {
        return Result.ok(skillService.export(skillId));
    }

    /**
     * 导入 Skill 定义（与创建同校验链路；同名拒绝 5013）
     */
    @PostMapping("/import")
    public Result<SkillVO> importSkill(@Valid @RequestBody SkillImportDTO dto) {
        return Result.ok(skillService.importSkill(dto));
    }

    /**
     * 导入技能包（Skill 中心 v2）：整个文件夹（files + paths）或单个 .zip（file）。
     * 解析 SKILL.md frontmatter 带出名称/描述，自动识别内容型 vs 脚本包，
     * 落 skill + skill_package_file；zip slip 防护。同名拒绝 5013，包非法 5014。
     */
    @PostMapping("/import-package")
    public Result<SkillVO> importPackage(
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) List<MultipartFile> files,
            @RequestParam(required = false) List<String> paths) {
        return Result.ok(skillService.importPackage(file, files, paths));
    }

    /**
     * 技能包文件树（详情抽屉展示 SKILL.md + scripts + resources，脚本仅存储不执行）
     */
    @GetMapping("/{skillId}/package-files")
    public Result<List<SkillPackageFileVO>> listPackageFiles(@PathVariable String skillId) {
        return Result.ok(skillService.listPackageFiles(skillId));
    }

    /**
     * 启用/停用技能（我的技能 toggle）：用户 Skill 切换 status；
     * MCP 工具切换发布状态；内置技能系统托管拒绝（5006）
     */
    @PutMapping("/{skillId}/status")
    public Result<SkillVO> setStatus(@PathVariable String skillId,
                                     @RequestParam boolean enabled) {
        return Result.ok(skillService.setStatus(skillId, enabled));
    }

    /**
     * 广场调用（试一试）。动作型技能两阶段：
     * 首次返回草稿 + confirmToken（不执行）；带有效 token 才真实执行并写审计。
     */
    @PostMapping("/{skillId}/invoke")
    public Result<InvokeSkillVO> invoke(@PathVariable String skillId,
                                        @RequestBody(required = false) InvokeSkillDTO dto) {
        return Result.ok(skillService.invoke(skillId,
                dto != null ? dto.getParams() : null,
                dto != null ? dto.getConfirmToken() : null));
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
     * 技能广场（Skill 中心 v2，发现视角）：
     * 本空间 active 用户 Skill + builtin + 已发布的 MCP 工具；支持 q（搜索）与 cat（工种）过滤
     */
    @GetMapping("/plaza")
    public Result<List<SkillVO>> listPlaza(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) String cat) {
        return Result.ok(agentSkillService.listPlaza(RuntimeContext.getWorkspaceId(), q, cat));
    }

    /**
     * 获取工作空间的 Skill 列表
     * 课题⑧：支持 keyword（名称/描述模糊）+ category（业务分类）+ type（api/prompt/builtin/mcp）筛选；
     * status 可选（如 active），不传则包含已停用技能（管理视角）
     */
    @GetMapping
    public Result<PageResult<SkillVO>> listSkills(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        String workspaceId = RuntimeContext.getWorkspaceId();
        return Result.ok(agentSkillService.listSkills(workspaceId, keyword, category, type, status, page, size));
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
