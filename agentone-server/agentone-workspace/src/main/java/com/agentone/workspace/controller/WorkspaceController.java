package com.agentone.workspace.controller;

import com.agentone.common.context.RuntimeContext;
import com.agentone.common.result.Result;
import com.agentone.common.service.WorkspaceService;
import com.agentone.workspace.dto.WorkspaceDTO;
import com.agentone.workspace.entity.UserWorkspaceDO;
import com.agentone.workspace.entity.WorkspaceDO;
import com.agentone.workspace.mapper.UserWorkspaceMapper;
import com.agentone.workspace.mapper.WorkspaceMapper;
import com.agentone.workspace.vo.WorkspaceVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作空间 Controller
 */
@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceMapper workspaceMapper;
    private final UserWorkspaceMapper userWorkspaceMapper;
    private final WorkspaceService workspaceService;

    /**
     * 获取当前用户的工作空间列表
     */
    @GetMapping
    public Result<List<WorkspaceVO>> list() {
        String userId = RuntimeContext.getUserId();
        List<String> wsIds = workspaceService.getUserWorkspaceIds(userId);
        if (wsIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<WorkspaceDO> workspaces = workspaceMapper.selectBatchIds(wsIds);

        // 查询角色
        Map<String, String> roleMap = userWorkspaceMapper.selectList(
                new LambdaQueryWrapper<UserWorkspaceDO>()
                        .eq(UserWorkspaceDO::getUserId, userId)
        ).stream().collect(Collectors.toMap(UserWorkspaceDO::getWorkspaceId, UserWorkspaceDO::getRole));

        List<WorkspaceVO> voList = workspaces.stream().map(ws -> {
            WorkspaceVO vo = new WorkspaceVO();
            vo.setId(ws.getId());
            vo.setName(ws.getName());
            vo.setDescription(ws.getDescription());
            vo.setRole(roleMap.getOrDefault(ws.getId(), "member"));
            vo.setCreatedAt(ws.getCreatedAt());
            return vo;
        }).collect(Collectors.toList());

        return Result.ok(voList);
    }

    /**
     * 创建工作空间
     */
    @PostMapping
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<WorkspaceVO> create(@Valid @RequestBody WorkspaceDTO dto) {
        String userId = RuntimeContext.getUserId();

        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setName(dto.getName());
        workspace.setDescription(dto.getDescription());
        workspace.setOwnerId(userId);
        workspace.setSettings("{}");
        workspace.setCreatedAt(LocalDateTime.now());
        workspace.setUpdatedAt(LocalDateTime.now());
        workspaceMapper.insert(workspace);

        UserWorkspaceDO uw = new UserWorkspaceDO();
        uw.setUserId(userId);
        uw.setWorkspaceId(workspace.getId());
        uw.setRole("owner");
        uw.setCreatedAt(LocalDateTime.now());
        userWorkspaceMapper.insert(uw);

        WorkspaceVO vo = new WorkspaceVO();
        vo.setId(workspace.getId());
        vo.setName(workspace.getName());
        vo.setDescription(workspace.getDescription());
        vo.setRole("owner");
        vo.setCreatedAt(workspace.getCreatedAt());
        return Result.ok(vo);
    }

    /**
     * 更新工作空间
     */
    @PutMapping("/{id}")
    public Result<WorkspaceVO> update(@PathVariable String id, @Valid @RequestBody WorkspaceDTO dto) {
        if (!workspaceService.checkPermission(RuntimeContext.getUserId(), id)) {
            return Result.fail(2002, "无权操作该工作空间");
        }

        WorkspaceDO workspace = workspaceMapper.selectById(id);
        if (workspace == null) {
            return Result.fail(2001, "工作空间不存在");
        }
        workspace.setName(dto.getName());
        workspace.setDescription(dto.getDescription());
        workspace.setUpdatedAt(LocalDateTime.now());
        workspaceMapper.updateById(workspace);

        WorkspaceVO vo = new WorkspaceVO();
        vo.setId(workspace.getId());
        vo.setName(workspace.getName());
        vo.setDescription(workspace.getDescription());
        vo.setCreatedAt(workspace.getCreatedAt());
        return Result.ok(vo);
    }

    /**
     * 删除工作空间（软删除，仅 owner 可操作）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        String userId = RuntimeContext.getUserId();
        if (!workspaceService.checkPermission(userId, id)) {
            return Result.fail(2002, "无权操作该工作空间");
        }

        // 仅 owner 可删除工作空间
        String role = workspaceService.getUserRole(userId, id);
        if (!"owner".equals(role)) {
            return Result.fail(2003, "仅工作空间所有者可删除");
        }

        WorkspaceDO workspace = workspaceMapper.selectById(id);
        if (workspace == null) {
            return Result.fail(2001, "工作空间不存在");
        }
        // 软删除工作空间并清理 user_workspace 关联（事务内完成，默认工作空间自动重算）
        workspaceService.deleteWorkspace(id);
        return Result.ok();
    }
}
