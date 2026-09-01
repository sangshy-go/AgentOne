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
     * 获取工作空间的 Skill 列表（课题⑧：支持关键字搜索 + 业务分类/类型筛选）。
     * keyword 模糊匹配名称与描述；category/type/status 为精确匹配，null/空白表示不过滤。
     * status 传 "active" 时仅返回启用中的（绑定选择器等场景）；
     * 不传则包含已停用技能（「我的技能」管理视角需展示停用项以便重新启用）。
     * 虚拟挂载（builtin/mcp）与 DB 记录应用同样的过滤条件。
     */
    PageResult<SkillVO> listSkills(String workspaceId, String keyword, String category,
                                   String type, String status, Integer page, Integer size);

    /**
     * 技能广场列表（Skill 中心 v2，发现视角）。
     *
     * 可见范围 = 本空间 active 的用户 Skill + builtin（默认可见）
     * + 已发布（mcp_tool_publish.published=true）的 MCP 工具；
     * 未发布的 MCP 工具不出现（供给侧治理，IT 显式发布后才上架）。
     * 广场不分页：工作空间技能量为中小规模，前端整页渲染卡片网格。
     */
    List<SkillVO> listPlaza(String workspaceId, String keyword, String category);
}
