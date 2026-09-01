package com.agentone.workspace.service.impl;

import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.common.result.PageResult;
import com.agentone.common.service.UserLookupService;
import com.agentone.workspace.dto.MemberDTO;
import com.agentone.workspace.entity.UserWorkspaceDO;
import com.agentone.workspace.mapper.UserWorkspaceMapper;
import com.agentone.workspace.service.MemberService;
import com.agentone.workspace.vo.MemberVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工作空间成员管理服务实现。
 * 管理员门禁（admin/owner 才能进入本模块接口）由 WorkspaceRbacFilter 统一负责，
 * 此处只保留业务规则：角色取值、owner 保护、自移除禁止。
 */
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    /** 可通过成员管理授予的角色（owner 不可授予/变更）；课题⑩ 增 auditor 审计员 */
    private static final Set<String> ASSIGNABLE_ROLES = Set.of("admin", "developer", "observer", "auditor");

    private final UserWorkspaceMapper userWorkspaceMapper;
    private final UserLookupService userLookupService;

    @Override
    public PageResult<MemberVO> listMembers(Integer current, Integer size) {
        String workspaceId = RuntimeContext.getWorkspaceId();
        Page<UserWorkspaceDO> page = userWorkspaceMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<UserWorkspaceDO>()
                        .eq(UserWorkspaceDO::getWorkspaceId, workspaceId)
                        .orderByAsc(UserWorkspaceDO::getCreatedAt));

        // 批量富化用户信息，避免循环单查
        List<String> userIds = page.getRecords().stream()
                .map(UserWorkspaceDO::getUserId).collect(Collectors.toList());
        Map<String, UserLookupService.UserInfo> userMap = userLookupService.findByIds(userIds).stream()
                .collect(Collectors.toMap(UserLookupService.UserInfo::userId, Function.identity()));

        Page<MemberVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(uw -> {
            MemberVO vo = new MemberVO();
            vo.setUserId(uw.getUserId());
            UserLookupService.UserInfo user = userMap.get(uw.getUserId());
            vo.setEmail(user != null ? user.email() : "");
            vo.setNickname(user != null ? user.nickname() : "");
            vo.setRole(uw.getRole());
            vo.setJoinedAt(uw.getCreatedAt());
            return vo;
        }).collect(Collectors.toList()));
        return PageResult.of(voPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberVO addMember(MemberDTO dto) {
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new BusinessException(2006, "邮箱不能为空");
        }
        validateRole(dto.getRole());

        UserLookupService.UserInfo user = userLookupService.findByEmail(dto.getEmail().trim());
        if (user == null) {
            throw new BusinessException(2006, "该邮箱未注册，请让用户先注册账号");
        }
        String workspaceId = RuntimeContext.getWorkspaceId();
        Long exists = userWorkspaceMapper.selectCount(
                new LambdaQueryWrapper<UserWorkspaceDO>()
                        .eq(UserWorkspaceDO::getWorkspaceId, workspaceId)
                        .eq(UserWorkspaceDO::getUserId, user.userId()));
        if (exists > 0) {
            throw new BusinessException(2007, "该用户已是工作空间成员");
        }

        UserWorkspaceDO uw = new UserWorkspaceDO();
        uw.setUserId(user.userId());
        uw.setWorkspaceId(workspaceId);
        uw.setRole(dto.getRole());
        uw.setCreatedAt(LocalDateTime.now());
        userWorkspaceMapper.insert(uw);

        MemberVO vo = new MemberVO();
        vo.setUserId(user.userId());
        vo.setEmail(user.email());
        vo.setNickname(user.nickname());
        vo.setRole(dto.getRole());
        vo.setJoinedAt(uw.getCreatedAt());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(String userId, MemberDTO dto) {
        validateRole(dto.getRole());
        UserWorkspaceDO uw = getMembership(userId);
        if ("owner".equals(uw.getRole())) {
            throw new BusinessException(2008, "不能变更所有者的角色");
        }
        uw.setRole(dto.getRole());
        userWorkspaceMapper.updateById(uw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(String userId) {
        UserWorkspaceDO uw = getMembership(userId);
        if ("owner".equals(uw.getRole())) {
            throw new BusinessException(2008, "不能移除所有者");
        }
        if (userId.equals(RuntimeContext.getUserId())) {
            throw new BusinessException(2009, "不能移除自己，请让其他管理员操作");
        }
        userWorkspaceMapper.deleteById(uw.getId());
    }

    private UserWorkspaceDO getMembership(String userId) {
        UserWorkspaceDO uw = userWorkspaceMapper.selectOne(
                new LambdaQueryWrapper<UserWorkspaceDO>()
                        .eq(UserWorkspaceDO::getWorkspaceId, RuntimeContext.getWorkspaceId())
                        .eq(UserWorkspaceDO::getUserId, userId));
        if (uw == null) {
            throw new BusinessException(2010, "该用户不是工作空间成员");
        }
        return uw;
    }

    private void validateRole(String role) {
        if (role == null || !ASSIGNABLE_ROLES.contains(role)) {
            throw new BusinessException(2005, "无效的角色，可选值：admin / developer / observer / auditor");
        }
    }
}
