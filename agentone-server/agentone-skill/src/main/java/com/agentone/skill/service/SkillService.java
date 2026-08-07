package com.agentone.skill.service;

import com.agentone.skill.core.SkillResult;
import com.agentone.skill.dto.SkillDTO;
import com.agentone.skill.vo.SkillVO;

import java.util.Map;

/**
 * Skill 中心服务：API 模式 Skill 的生命周期管理。
 * builtin Skill 不落库、不可修改，不在本服务范围内。
 */
public interface SkillService {

    /**
     * 创建 API 模式 Skill，并注册到 SkillRegistry（立即可被 Agent 绑定使用）
     */
    SkillVO create(SkillDTO dto);

    /**
     * 更新 API 模式 Skill，并刷新 Registry 中的执行器
     */
    SkillVO update(String skillId, SkillDTO dto);

    /**
     * 删除 API 模式 Skill（存在 Agent 绑定时拒绝），并从 Registry 注销
     */
    void delete(String skillId);

    /**
     * 测试调用：直接执行 Skill 并返回真实结果（调试器 / 创建向导用）
     */
    SkillResult test(String skillId, Map<String, Object> params);
}
