package com.agentone.skill.service;

import com.agentone.skill.dto.DebugPreviewDTO;
import com.agentone.skill.dto.DebugRunDTO;
import com.agentone.skill.vo.DebugPreviewVO;
import com.agentone.skill.vo.DebugRunVO;
import com.agentone.skill.vo.DebugTargetVO;

import java.util.List;

/**
 * Skill 调试器服务（课题③）。
 *
 * 三步向导：
 * 1. listTargets —— 列出当前工作空间可调试的 Skill（Registry 全集：builtin/api/mcp）
 * 2. preview     —— 参数校验 + 执行计划预览（不发起真实调用）
 * 3. run         —— 真实执行，上下文取当前登录态（不允许跨租户注入），写调试审计
 */
public interface SkillDebugService {

    List<DebugTargetVO> listTargets();

    DebugPreviewVO preview(DebugPreviewDTO dto);

    DebugRunVO run(DebugRunDTO dto);
}
