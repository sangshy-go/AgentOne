package com.agentone.skill.service.impl;

import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.skill.core.SkillDescriptor;
import com.agentone.skill.core.SkillExecutor;
import com.agentone.skill.core.SkillRegistry;
import com.agentone.skill.dto.BindSkillDTO;
import com.agentone.skill.entity.AgentSkillBindingDO;
import com.agentone.skill.entity.McpToolPublishDO;
import com.agentone.skill.entity.SkillDO;
import com.agentone.skill.executor.McpSkillExecutor;
import com.agentone.skill.executor.UserSkillExecutors;
import com.agentone.skill.mapper.AgentSkillBindingMapper;
import com.agentone.skill.mapper.McpToolPublishMapper;
import com.agentone.skill.mapper.SkillCallLogMapper;
import com.agentone.skill.mapper.SkillMapper;
import com.agentone.skill.service.AgentSkillService;
import com.agentone.skill.vo.AgentSkillBindingVO;
import com.agentone.skill.vo.SkillVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.agentone.common.result.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent-Skill 绑定服务实现。
 *
 * builtin Skill 与 MCP 工具（课题④）采用"虚拟挂载"：不落 skill 表，
 * 列表/绑定时从 SkillRegistry 合并，绑定关系仍落 agent_skill_binding
 * （租户拦截器保证隔离）；skill 表只存 api / market 等用户创建的 Skill。
 *
 * 租户边界：虚拟 Skill 中 MCP 工具的描述符携带归属 workspaceId，
 * 列表只合并本空间的，绑定前校验归属（builtin workspaceId=null 全局可见）。
 *
 * Skill 中心 v2 治理：MCP 工具须先发布（mcp_tool_publish.published=true）
 * 才在广场可见、才可被绑定启用。
 */
@Service
@RequiredArgsConstructor
public class AgentSkillServiceImpl implements AgentSkillService {

    private final AgentSkillBindingMapper bindingMapper;
    private final SkillMapper skillMapper;
    private final SkillRegistry skillRegistry;
    private final ObjectMapper objectMapper;
    private final McpToolPublishMapper mcpToolPublishMapper;
    private final SkillCallLogMapper callLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentSkillBindingVO bind(BindSkillDTO dto) {
        // 检查是否已绑定
        Long count = bindingMapper.selectCount(
                new LambdaQueryWrapper<AgentSkillBindingDO>()
                        .eq(AgentSkillBindingDO::getAgentId, dto.getAgentId())
                        .eq(AgentSkillBindingDO::getSkillId, dto.getSkillId())
        );
        if (count > 0) {
            throw new BusinessException(5001, "该 Agent 已绑定此 Skill");
        }

        // Skill 存在性：DB 行（api/market）或 Registry 虚拟挂载（builtin/mcp）
        SkillDO skill = skillMapper.selectById(dto.getSkillId());
        SkillDescriptor virtual = skill == null ? descriptorOf(dto.getSkillId()) : null;
        if (skill == null && virtual == null) {
            throw new BusinessException(5002, "Skill 不存在");
        }

        // S3: 跨租户防护——DB 行 Skill 仅允许绑定当前工作空间的；
        // 虚拟 Skill 按描述符 workspaceId 校验（MCP 工具归属创建它的空间，
        // builtin workspaceId=null 全局可见）
        String wsId = RuntimeContext.getWorkspaceId();
        if (skill != null && !wsId.equals(skill.getWorkspaceId())) {
            throw new BusinessException(5004, "无权绑定其他工作空间的 Skill");
        }
        if (virtual != null && virtual.getWorkspaceId() != null
                && !wsId.equals(virtual.getWorkspaceId())) {
            throw new BusinessException(5004, "无权绑定其他工作空间的 Skill");
        }

        // Skill 中心 v2 治理：MCP 工具仅发布到广场后才可绑定启用
        if (dto.getSkillId() != null
                && dto.getSkillId().startsWith(McpSkillExecutor.SKILL_ID_PREFIX)
                && !publishedMcpSkillIds(wsId).contains(dto.getSkillId())) {
            throw new BusinessException(5016, "该 MCP 工具未发布到广场，请先在 MCP 管理中发布");
        }

        // 创建绑定
        AgentSkillBindingDO binding = new AgentSkillBindingDO();
        binding.setWorkspaceId(wsId);
        binding.setAgentId(dto.getAgentId());
        binding.setSkillId(dto.getSkillId());
        binding.setSkillVersion(dto.getSkillVersion() != null ? dto.getSkillVersion()
                : skill != null ? skill.getVersion() : virtual.getVersion());
        binding.setConfigOverride(dto.getConfigOverride() != null ? dto.getConfigOverride() : "{}");
        binding.setEnabled(true);
        binding.setCreatedAt(LocalDateTime.now());

        bindingMapper.insert(binding);

        return toVO(binding, skill);
    }

    @Override
    public void unbind(String bindingId) {
        // 作用域限定当前工作空间：跨空间 bindingId 命中 0 行，须显式失败而非静默成功。
        String wsId = RuntimeContext.getWorkspaceId();
        int rows = bindingMapper.delete(new LambdaQueryWrapper<AgentSkillBindingDO>()
                .eq(AgentSkillBindingDO::getId, bindingId)
                .eq(AgentSkillBindingDO::getWorkspaceId, wsId));
        if (rows == 0) {
            throw new BusinessException(5003, "绑定记录不存在");
        }
    }

    @Override
    public List<AgentSkillBindingVO> listBindings(String agentId) {
        List<AgentSkillBindingDO> bindings = bindingMapper.selectList(
                new LambdaQueryWrapper<AgentSkillBindingDO>()
                        .eq(AgentSkillBindingDO::getAgentId, agentId)
                        .orderByDesc(AgentSkillBindingDO::getCreatedAt)
        );

        return bindings.stream().map(binding -> {
            SkillDO skill = skillMapper.selectById(binding.getSkillId());
            return toVO(binding, skill);
        }).collect(Collectors.toList());
    }

    @Override
    public void removeAllBindings(String agentId) {
        bindingMapper.delete(
                new LambdaQueryWrapper<AgentSkillBindingDO>()
                        .eq(AgentSkillBindingDO::getAgentId, agentId)
        );
    }

    @Override
    public void toggleEnabled(String bindingId, boolean enabled) {
        // 按当前工作空间作用域更新：跨空间 bindingId 命中 0 行，须显式失败而非静默成功。
        String wsId = RuntimeContext.getWorkspaceId();
        AgentSkillBindingDO upd = new AgentSkillBindingDO();
        upd.setEnabled(enabled);
        int rows = bindingMapper.update(upd, new LambdaQueryWrapper<AgentSkillBindingDO>()
                .eq(AgentSkillBindingDO::getId, bindingId)
                .eq(AgentSkillBindingDO::getWorkspaceId, wsId));
        if (rows == 0) {
            throw new BusinessException(5003, "绑定记录不存在");
        }
    }

    @Override
    public PageResult<SkillVO> listSkills(String workspaceId, String keyword, String category,
                                          String type, String status, Integer page, Integer size) {
        Map<String, Long> callCounts = callCounts();

        // 默认不过滤状态：「我的技能」管理视角需保留已停用技能以便重新启用；
        // 绑定选择器等只需可用技能的场景显式传 status=active
        LambdaQueryWrapper<SkillDO> wrapper = new LambdaQueryWrapper<SkillDO>()
                .eq(SkillDO::getWorkspaceId, workspaceId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(SkillDO::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            // 大小写不敏感：与虚拟挂载的内存过滤保持一致（{0} 占位符参数化，无注入）
            String kw = "%" + keyword.trim().toLowerCase() + "%";
            wrapper.and(w -> w.apply("LOWER(name) LIKE {0}", kw)
                    .or().apply("LOWER(description) LIKE {0}", kw));
        }
        if (category != null && !category.isBlank()) {
            wrapper.eq(SkillDO::getCategory, category);
        }
        if (type != null && !type.isBlank()) {
            wrapper.eq(SkillDO::getType, type);
        }
        wrapper.orderByDesc(SkillDO::getInstalledAt);

        // 取全部命中的 DB 行（内存统一分页，与虚拟挂载合并为同一窗口）
        List<SkillVO> dbRecords = skillMapper.selectList(wrapper).stream()
                .map(skill -> toSkillVO(skill, callCounts))
                .collect(Collectors.toList());

        // 虚拟挂载（builtin + 本空间 MCP 工具）：应用同样的 keyword/category/type 过滤。
        // 虚拟 Skill 恒为 active，按非 active 状态过滤时不合并。
        boolean includeVirtuals = status == null || status.isBlank() || "active".equals(status);
        List<SkillVO> virtuals = includeVirtuals
                ? virtualVOs(workspaceId, keyword, category, type, callCounts, false, null)
                : List.of();

        // 统一合并为一个有序列表（虚拟置顶，随后 DB 行），再按页窗口裁剪，
        // 保证各页窗口正确、total 一致，且虚拟 Skill 仅在合并列表中按其位置出现一次。
        List<SkillVO> all = new ArrayList<>(virtuals);
        all.addAll(dbRecords);

        int total = all.size();
        int from = (page - 1) * size;
        if (from < 0) {
            from = 0;
        }
        int to = Math.min(from + size, total);
        List<SkillVO> records = from >= total ? List.of() : new ArrayList<>(all.subList(from, to));

        Page<SkillVO> voPage = new Page<>(page, size, total);
        voPage.setRecords(records);
        return PageResult.of(voPage);
    }

    @Override
    public List<SkillVO> listPlaza(String workspaceId, String keyword, String category) {
        Map<String, Long> callCounts = callCounts();
        List<SkillVO> result = new ArrayList<>();

        // 1) 本空间用户 Skill（自建/导入/官方）：active 即可见
        LambdaQueryWrapper<SkillDO> wrapper = new LambdaQueryWrapper<SkillDO>()
                .eq(SkillDO::getWorkspaceId, workspaceId)
                .eq(SkillDO::getStatus, "active");
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim().toLowerCase() + "%";
            wrapper.and(w -> w.apply("LOWER(name) LIKE {0}", kw)
                    .or().apply("LOWER(description) LIKE {0}", kw));
        }
        if (category != null && !category.isBlank()) {
            wrapper.eq(SkillDO::getCategory, category);
        }
        wrapper.orderByDesc(SkillDO::getInstalledAt);
        skillMapper.selectList(wrapper).forEach(skill -> result.add(toSkillVO(skill, callCounts)));

        // 2) 虚拟挂载：builtin 默认可见；MCP 工具仅已发布的可见（供给侧治理）
        Set<String> publishedIds = publishedMcpSkillIds(workspaceId);
        result.addAll(virtualVOs(workspaceId, keyword, category, null, callCounts, true, publishedIds));
        return result;
    }

    /**
     * 从 Registry 合成虚拟挂载 Skill 的 VO（不落库）：
     * builtin 全局可见；MCP 工具仅合并归属当前工作空间的（描述符带 workspaceId）。
     * 课题⑧：与 DB 查询一致的 keyword/category/type 内存过滤。
     *
     * @param plazaOnly    true=广场模式：MCP 工具仅保留 publishedIds 中的；false=管理模式：全部保留
     * @param publishedIds 已发布 MCP 虚拟 Skill ID 集合（plazaOnly=true 时必传）
     */
    private List<SkillVO> virtualVOs(String workspaceId, String keyword, String category, String type,
                                     Map<String, Long> callCounts, boolean plazaOnly, Set<String> publishedIds) {
        // 管理模式也需要发布状态用于 VO 标记；广场模式复用调用方已查的集合，避免重复查询
        Set<String> published = publishedIds != null ? publishedIds : publishedMcpSkillIds(workspaceId);
        String kw = keyword != null ? keyword.trim().toLowerCase() : null;
        // 只合并虚拟类型：builtin 全局 + mcp 限本空间。
        // api 类型虽也在 Registry，但已由 DB 分页查询返回，且跨租户注册，不可在此合并。
        return skillRegistry.listDescriptors().stream()
                .filter(d -> "builtin".equals(d.getType())
                        || ("mcp".equals(d.getType()) && workspaceId.equals(d.getWorkspaceId())))
                .filter(d -> !"mcp".equals(d.getType()) || !plazaOnly
                        || published.contains(d.getId()))
                .filter(d -> type == null || type.isBlank() || type.equals(d.getType()))
                .filter(d -> category == null || category.isBlank()
                        || category.equals(d.getCategory()))
                .filter(d -> kw == null || kw.isEmpty()
                        || (d.getName() != null && d.getName().toLowerCase().contains(kw))
                        || (d.getDescription() != null
                                && d.getDescription().toLowerCase().contains(kw)))
                .map(d -> {
                    SkillVO vo = new SkillVO();
                    vo.setId(d.getId());
                    vo.setName(d.getName());
                    vo.setType(d.getType());
                    vo.setSource(d.getSource());
                    vo.setCategory(d.getCategory());
                    vo.setDescription(d.getDescription());
                    vo.setInputSchema(toJson(d.getInputSchema()));
                    vo.setOutputSchema(toJson(d.getOutputSchema()));
                    vo.setConfig("{}");
                    vo.setVersion(d.getVersion());
                    vo.setStatus("active");
                    vo.setActionType(d.isActionType());
                    vo.setCallCount(callCounts.getOrDefault(d.getId(), 0L));
                    if ("mcp".equals(d.getType())) {
                        vo.setPublished(published.contains(d.getId()));
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /** 已发布的 MCP 虚拟 Skill ID 集合：mcp_tool_publish.published=true → mcp-{serverId}-{toolName} */
    private Set<String> publishedMcpSkillIds(String workspaceId) {
        List<McpToolPublishDO> rows = mcpToolPublishMapper.selectList(
                new LambdaQueryWrapper<McpToolPublishDO>()
                        .eq(McpToolPublishDO::getWorkspaceId, workspaceId)
                        .eq(McpToolPublishDO::getPublished, true));
        Set<String> ids = new HashSet<>();
        for (McpToolPublishDO row : rows) {
            ids.add(McpSkillExecutor.skillIdOf(row.getServerId(), row.getToolName()));
        }
        return ids;
    }

    /** 当前工作空间各技能的真实调用计数（skill_call_log 聚合） */
    private Map<String, Long> callCounts() {
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> row : callLogMapper.countCallsGroupBySkill()) {
            Object id = row.get("skillId");
            Object count = row.get("callCount");
            if (id != null && count instanceof Number n) {
                counts.put(id.toString(), n.longValue());
            }
        }
        return counts;
    }

    private SkillDescriptor descriptorOf(String skillId) {
        return skillRegistry.getExecutor(skillId)
                .map(SkillExecutor::getDescriptor)
                .orElse(null);
    }

    private AgentSkillBindingVO toVO(AgentSkillBindingDO binding, SkillDO skill) {
        AgentSkillBindingVO vo = new AgentSkillBindingVO();
        BeanUtils.copyProperties(binding, vo);
        if (skill != null) {
            vo.setSkillName(skill.getName());
            vo.setSkillType(skill.getType());
        } else {
            // builtin 虚拟挂载：名称/类型从 Registry 取
            SkillDescriptor descriptor = descriptorOf(binding.getSkillId());
            if (descriptor != null) {
                vo.setSkillName(descriptor.getName());
                vo.setSkillType(descriptor.getType());
            }
        }
        return vo;
    }

    private SkillVO toSkillVO(SkillDO skill, Map<String, Long> callCounts) {
        SkillVO vo = new SkillVO();
        BeanUtils.copyProperties(skill, vo);
        vo.setActionType(UserSkillExecutors.isActionType(skill, objectMapper));
        vo.setCallCount(callCounts.getOrDefault(skill.getId(), 0L));
        return vo;
    }

    private String toJson(Object schema) {
        if (schema == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            return "{}";
        }
    }
}
