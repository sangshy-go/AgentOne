package com.agentone.workspace.service.impl;

import com.agentone.common.exception.BusinessException;
import com.agentone.common.service.WorkspaceService;
import com.agentone.workspace.entity.UserWorkspaceDO;
import com.agentone.workspace.entity.WorkspaceDO;
import com.agentone.workspace.mapper.UserWorkspaceMapper;
import com.agentone.workspace.mapper.WorkspaceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 工作空间服务实现
 */
@Service
@RequiredArgsConstructor
public class WorkspaceServiceImpl implements WorkspaceService {

    private final WorkspaceMapper workspaceMapper;
    private final UserWorkspaceMapper userWorkspaceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createDefaultWorkspace(String userId, String email) {
        // 创建工作空间
        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setName(email.split("@")[0] + " 的工作空间");
        workspace.setDescription("默认工作空间");
        workspace.setOwnerId(userId);
        workspace.setSettings("{}");
        workspace.setCreatedAt(LocalDateTime.now());
        workspace.setUpdatedAt(LocalDateTime.now());
        workspaceMapper.insert(workspace);

        // 创建用户-空间关联（角色: owner）
        UserWorkspaceDO uw = new UserWorkspaceDO();
        uw.setUserId(userId);
        uw.setWorkspaceId(workspace.getId());
        uw.setRole("owner");
        uw.setCreatedAt(LocalDateTime.now());
        userWorkspaceMapper.insert(uw);

        return workspace.getId();
    }

    @Override
    public String getWorkspaceName(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return "";
        }
        WorkspaceDO ws = workspaceMapper.selectById(workspaceId);
        return ws != null ? ws.getName() : "";
    }

    @Override
    public boolean checkPermission(String userId, String workspaceId) {
        // 1. 检查用户与工作空间的关联关系
        Long count = userWorkspaceMapper.selectCount(
                new LambdaQueryWrapper<UserWorkspaceDO>()
                        .eq(UserWorkspaceDO::getUserId, userId)
                        .eq(UserWorkspaceDO::getWorkspaceId, workspaceId)
        );
        if (count == 0) return false;

        // 2. 检查工作空间是否已被软删除（@TableLogic 自动过滤 deletedAt IS NOT NULL）
        WorkspaceDO ws = workspaceMapper.selectById(workspaceId);
        return ws != null;
    }

    @Override
    public String getUserRole(String userId, String workspaceId) {
        UserWorkspaceDO uw = userWorkspaceMapper.selectOne(
                new LambdaQueryWrapper<UserWorkspaceDO>()
                        .eq(UserWorkspaceDO::getUserId, userId)
                        .eq(UserWorkspaceDO::getWorkspaceId, workspaceId)
        );
        return uw != null ? uw.getRole() : null;
    }

    @Override
    public List<String> getUserWorkspaceIds(String userId) {
        List<UserWorkspaceDO> list = userWorkspaceMapper.selectList(
                new LambdaQueryWrapper<UserWorkspaceDO>()
                        .eq(UserWorkspaceDO::getUserId, userId)
        );
        return list.stream().map(UserWorkspaceDO::getWorkspaceId).collect(Collectors.toList());
    }

    @Override
    public String getDefaultWorkspaceId(String userId) {
        List<String> ids = getUserWorkspaceIds(userId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWorkspace(String workspaceId) {
        WorkspaceDO workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw new BusinessException(2001, "工作空间不存在");
        }
        // @TableLogic(delval="now()")：deleteById 实际执行
        // UPDATE workspace SET deleted_at = now() WHERE id = ? AND deleted_at IS NULL。
        // 不能手工 setDeletedAt + updateById——逻辑删除字段被排除在 updateById 的 SET 子句外，不会生效。
        workspaceMapper.deleteById(workspaceId);

        // 清理 user_workspace 关联，避免工作空间删除后关联行残留，
        // 导致用户默认工作空间（由关联顺序派生）悬空、指向已删除的工作空间。
        // 删除后默认工作空间会自动从剩余关联重算（无剩余则为 null，安全）。
        userWorkspaceMapper.delete(
                new LambdaQueryWrapper<UserWorkspaceDO>()
                        .eq(UserWorkspaceDO::getWorkspaceId, workspaceId)
        );
    }
}
