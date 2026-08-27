package com.agentone.api.service;

import com.agentone.api.vo.AuditLogVO;
import com.agentone.common.audit.AuditLogDO;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.common.mapper.AuditLogMapper;
import com.agentone.common.result.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审计日志查询服务（课题⑩）。
 * 放在 api 模块：与 DashboardService 同属聚合查询层。
 * 门禁：仅 owner/admin/auditor 可查（developer/observer 拒绝）；
 * 数据范围经租户拦截器自动限定当前工作空间。
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final Set<String> VIEW_ROLES = Set.of("owner", "admin", "auditor");

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public PageResult<AuditLogVO> list(String action, String resourceType, String keyword,
                                       Integer page, Integer size) {
        String role = RuntimeContext.getRole();
        if (role == null || !VIEW_ROLES.contains(role)) {
            throw new BusinessException(2003, "仅管理员、所有者或审计员可查看审计日志");
        }

        LambdaQueryWrapper<AuditLogDO> qw = new LambdaQueryWrapper<AuditLogDO>()
                .eq(AuditLogDO::getWorkspaceId, RuntimeContext.getWorkspaceId());
        if (action != null && !action.isBlank()) {
            qw.eq(AuditLogDO::getAction, action.trim());
        }
        if (resourceType != null && !resourceType.isBlank()) {
            qw.eq(AuditLogDO::getResourceType, resourceType.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like(AuditLogDO::getOperatorId, kw)
                    .or().like(AuditLogDO::getResourceId, kw));
        }
        qw.orderByDesc(AuditLogDO::getCreatedAt);

        Page<AuditLogDO> result = auditLogMapper.selectPage(new Page<>(page, size), qw);
        Page<AuditLogVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return PageResult.of(voPage);
    }

    private AuditLogVO toVO(AuditLogDO audit) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(audit.getId());
        vo.setOperatorId(audit.getOperatorId());
        vo.setAction(audit.getAction());
        vo.setResourceType(audit.getResourceType());
        vo.setResourceId(audit.getResourceId());
        vo.setCreatedAt(audit.getCreatedAt());
        // detail 为写入时落库的 {method, path, operatorEmail}，best-effort 解析
        if (audit.getDetail() != null && !audit.getDetail().isBlank()) {
            try {
                JsonNode detail = objectMapper.readTree(audit.getDetail());
                vo.setMethod(textOrNull(detail, "method"));
                vo.setPath(textOrNull(detail, "path"));
                vo.setOperatorEmail(textOrNull(detail, "operatorEmail"));
            } catch (Exception ignored) {
                // detail 解析失败不影响列表展示
            }
        }
        return vo;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child != null && !child.isNull() ? child.asText() : null;
    }
}
