package com.agentone.auth.service.impl;

import com.agentone.auth.entity.SysUserDO;
import com.agentone.auth.mapper.SysUserMapper;
import com.agentone.common.service.UserLookupService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 用户信息查询实现（供 workspace 等模块跨模块使用）
 */
@Service
@RequiredArgsConstructor
public class UserLookupServiceImpl implements UserLookupService {

    private final SysUserMapper sysUserMapper;

    @Override
    public UserInfo findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        SysUserDO user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUserDO>().eq(SysUserDO::getEmail, email));
        return user != null ? new UserInfo(user.getId(), user.getEmail(), user.getNickname()) : null;
    }

    @Override
    public List<UserInfo> findByIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysUserDO> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUserDO>().in(SysUserDO::getId, userIds));
        return users.stream()
                .map(u -> new UserInfo(u.getId(), u.getEmail(), u.getNickname()))
                .toList();
    }
}
