package com.agentone.workspace.service.impl;

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
        Long count = userWorkspaceMapper.selectCount(
                new LambdaQueryWrapper<UserWorkspaceDO>()
                        .eq(UserWorkspaceDO::getUserId, userId)
                        .eq(UserWorkspaceDO::getWorkspaceId, workspaceId)
        );
        return count > 0;
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
}
