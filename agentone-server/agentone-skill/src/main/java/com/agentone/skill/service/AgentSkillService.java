package com.agentone.skill.service;

import com.agentone.skill.dto.BindSkillDTO;
import com.agentone.skill.vo.AgentSkillBindingVO;
import com.agentone.skill.vo.SkillVO;
import com.agentone.common.result.PageResult;

import java.util.List;

/**
 * Agent-Skill 绑定服务
 */
public interface AgentSkillService {

    /**
     * 绑定 Skill 到 Agent
     */
    AgentSkillBindingVO bind(BindSkillDTO dto);

    /**
     * 解绑 Skill
     */
    void unbind(String bindingId);

    /**
     * 获取 Agent 绑定的 Skill 列表
     */
    List<AgentSkillBindingVO> listBindings(String agentId);

    /**
     * 删除 Agent 的全部 Skill 绑定（Agent 被物理删除时级联调用）
     */
    void removeAllBindings(String agentId);

    /**
     * 启用/禁用绑定
     */
    void toggleEnabled(String bindingId, boolean enabled);

    /**
     * 获取工作空间的所有 Skill
     */
    PageResult<SkillVO> listSkills(String workspaceId, Integer page, Integer size);
}
