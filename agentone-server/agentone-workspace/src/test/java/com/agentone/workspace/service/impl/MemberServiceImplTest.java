package com.agentone.workspace.service.impl;

import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.common.service.UserLookupService;
import com.agentone.workspace.dto.MemberDTO;
import com.agentone.workspace.entity.UserWorkspaceDO;
import com.agentone.workspace.mapper.UserWorkspaceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MemberServiceImpl 业务规则单测（课题⑥）：
 * 未注册邮箱 2006 / 重复成员 2007 / owner 保护 2008 / 禁止自移除 2009 / 角色取值 2005。
 * 管理员门禁由 WorkspaceRbacFilter 负责，不在本层重复。
 */
@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest {

    @Mock
    private UserWorkspaceMapper userWorkspaceMapper;
    @Mock
    private UserLookupService userLookupService;

    @InjectMocks
    private MemberServiceImpl service;

    @BeforeEach
    void setUp() {
        // 操作者 user-admin，隶属 ws-1
        RuntimeContext.set(Context.of("user-admin", "ws-1"));
    }

    @AfterEach
    void tearDown() {
        RuntimeContext.clear();
    }

    private MemberDTO dto(String email, String role) {
        MemberDTO dto = new MemberDTO();
        dto.setEmail(email);
        dto.setRole(role);
        return dto;
    }

    private UserWorkspaceDO membership(String userId, String role) {
        UserWorkspaceDO uw = new UserWorkspaceDO();
        uw.setId("uw-1");
        uw.setUserId(userId);
        uw.setWorkspaceId("ws-1");
        uw.setRole(role);
        return uw;
    }

    @Test
    void addMember_unregisteredEmail_throws2006() {
        when(userLookupService.findByEmail("ghost@test.com")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.addMember(dto("ghost@test.com", "developer")));
        assertEquals(2006, ex.getCode());
        verify(userWorkspaceMapper, never()).insert(any(UserWorkspaceDO.class));
    }

    @Test
    void addMember_alreadyMember_throws2007() {
        when(userLookupService.findByEmail("dev@test.com"))
                .thenReturn(new UserLookupService.UserInfo("user-2", "dev@test.com", "小开"));
        when(userWorkspaceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.addMember(dto("dev@test.com", "developer")));
        assertEquals(2007, ex.getCode());
        verify(userWorkspaceMapper, never()).insert(any(UserWorkspaceDO.class));
    }

    @Test
    void addMember_invalidRole_throws2005() {
        // owner 不可被授予
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.addMember(dto("dev@test.com", "owner")));
        assertEquals(2005, ex.getCode());
        verify(userLookupService, never()).findByEmail(any());
    }

    @Test
    void addMember_success_returnsVO() {
        when(userLookupService.findByEmail("dev@test.com"))
                .thenReturn(new UserLookupService.UserInfo("user-2", "dev@test.com", "小开"));
        when(userWorkspaceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        var vo = service.addMember(dto("dev@test.com", "observer"));
        assertEquals("user-2", vo.getUserId());
        assertEquals("observer", vo.getRole());
        verify(userWorkspaceMapper).insert(any(UserWorkspaceDO.class));
    }

    @Test
    void updateRole_ownerTarget_throws2008() {
        when(userWorkspaceMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(membership("user-owner", "owner"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateRole("user-owner", dto(null, "developer")));
        assertEquals(2008, ex.getCode());
        verify(userWorkspaceMapper, never()).updateById(any(UserWorkspaceDO.class));
    }

    @Test
    void removeMember_ownerTarget_throws2008() {
        when(userWorkspaceMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(membership("user-owner", "owner"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.removeMember("user-owner"));
        assertEquals(2008, ex.getCode());
        verify(userWorkspaceMapper, never()).deleteById(anyString());
    }

    @Test
    void removeMember_self_throws2009() {
        // 操作者 user-admin 试图移除自己
        when(userWorkspaceMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(membership("user-admin", "admin"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.removeMember("user-admin"));
        assertEquals(2009, ex.getCode());
        verify(userWorkspaceMapper, never()).deleteById(anyString());
    }

    @Test
    void removeMember_regularMember_deletes() {
        when(userWorkspaceMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(membership("user-2", "developer"));

        service.removeMember("user-2");
        verify(userWorkspaceMapper).deleteById("uw-1");
    }
}
