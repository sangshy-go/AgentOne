package com.agentone.auth.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.agentone.auth.dto.LoginDTO;
import com.agentone.auth.dto.RegisterDTO;
import com.agentone.auth.entity.SysUserDO;
import com.agentone.auth.mapper.SysUserMapper;
import com.agentone.auth.service.AuthService;
import com.agentone.auth.util.JwtUtil;
import com.agentone.auth.vo.AuthVO;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.common.result.ResultCode;
import com.agentone.common.service.WorkspaceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final WorkspaceService workspaceService;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    private static final String LOGIN_FAIL_KEY = "login:fail:";

    /** Q6: 登录失败锁定阈值（原硬编码 5），可通过 agentone.auth.max-login-fail 覆盖 */
    @org.springframework.beans.factory.annotation.Value("${agentone.auth.max-login-fail:5}")
    private int maxLoginFail;

    /** Q6: 登录失败锁定时长（分钟，原硬编码 30），可通过 agentone.auth.lock-minutes 覆盖 */
    @org.springframework.beans.factory.annotation.Value("${agentone.auth.lock-minutes:30}")
    private int lockMinutes;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthVO register(RegisterDTO dto) {
        // 1. 检查邮箱是否已注册
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUserDO>().eq(SysUserDO::getEmail, dto.getEmail())
        );
        if (count > 0) {
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        // 2. 创建用户
        SysUserDO user = new SysUserDO();
        user.setEmail(dto.getEmail());
        user.setPassword(BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt(12)));
        user.setNickname(dto.getNickname() != null ? dto.getNickname() : dto.getEmail().split("@")[0]);
        user.setStatus("active");
        user.setLoginFailCount(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        // 2. 创建用户（并发注册竞态：唯一索引兜底，捕获 DuplicateKeyException 转业务异常）
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        // 3. 创建默认工作空间
        String workspaceId = workspaceService.createDefaultWorkspace(user.getId(), user.getEmail());

        // 4. 生成 JWT（access + refresh，C2）
        String token = jwtUtil.generateToken(user.getId(), workspaceId, user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), workspaceId, user.getEmail());

        // 5. 返回 VO
        AuthVO vo = new AuthVO();
        vo.setToken(token);
        vo.setRefreshToken(refreshToken);
        vo.setUserId(user.getId());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setWorkspaceId(workspaceId);
        vo.setWorkspaceName(workspaceService.getWorkspaceName(workspaceId));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthVO login(LoginDTO dto) {
        // Q6: 锁定 key 绑定客户端 IP，避免攻击者用已知邮箱从单一 IP 刷接口即可锁定受害者账户
        String failKey = LOGIN_FAIL_KEY + getClientIp() + ":" + dto.getEmail();

        // 1. 检查是否被锁定
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        int failCount = 0;
        if (failCountStr != null) {
            try {
                failCount = Integer.parseInt(failCountStr);
            } catch (NumberFormatException e) {
                failCount = 0;
            }
        }
        if (failCount >= maxLoginFail) {
            throw new BusinessException(ResultCode.ACCOUNT_LOCKED);
        }

        // 2. 查询用户
        SysUserDO user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUserDO>().eq(SysUserDO::getEmail, dto.getEmail())
        );
        if (user == null) {
            incrementFailCount(failKey, null);
            throw new BusinessException(ResultCode.EMAIL_OR_PASSWORD_ERROR);
        }

        // 3. 校验密码
        if (!BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            incrementFailCount(failKey, user.getId());
            throw new BusinessException(ResultCode.EMAIL_OR_PASSWORD_ERROR);
        }

        // 4. 检查账号状态（DB 持久化锁定 + lockedUntil 时间窗口）
        if ("locked".equals(user.getStatus())) {
            // lockedUntil 为 null 也视为仍锁定（不自动解锁），避免 DB 脏数据导致绕过
            if (user.getLockedUntil() == null || user.getLockedUntil().isAfter(LocalDateTime.now())) {
                throw new BusinessException(ResultCode.ACCOUNT_LOCKED);
            }
            // lockedUntil 已过期，解锁
            user.setStatus("active");
            user.setLockedUntil(null);
            user.setLoginFailCount(0);
            userMapper.updateById(user);
        }

        // 5. 登录成功，清除失败计数
        redisTemplate.delete(failKey);
        user.setLoginFailCount(0);
        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 6. 获取默认工作空间
        String workspaceId = workspaceService.getDefaultWorkspaceId(user.getId());
        String token = jwtUtil.generateToken(user.getId(), workspaceId, user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), workspaceId, user.getEmail());

        AuthVO vo = new AuthVO();
        vo.setToken(token);
        vo.setRefreshToken(refreshToken);
        vo.setUserId(user.getId());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setWorkspaceId(workspaceId);
        vo.setWorkspaceName(workspaceService.getWorkspaceName(workspaceId));
        return vo;
    }

    @Override
    public AuthVO switchWorkspace(String userId, String targetWorkspaceId) {
        if (!workspaceService.checkPermission(userId, targetWorkspaceId)) {
            throw new BusinessException(ResultCode.WORKSPACE_NO_PERMISSION);
        }

        SysUserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 更新 RuntimeContext，使后续操作使用新的 workspaceId
        com.agentone.common.context.Context ctx =
                com.agentone.common.context.Context.of(userId, targetWorkspaceId);
        RuntimeContext.set(ctx);

        String token = jwtUtil.generateToken(userId, targetWorkspaceId, user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(userId, targetWorkspaceId, user.getEmail());

        AuthVO vo = new AuthVO();
        vo.setToken(token);
        vo.setRefreshToken(refreshToken);
        vo.setUserId(userId);
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setWorkspaceId(targetWorkspaceId);
        vo.setWorkspaceName(workspaceService.getWorkspaceName(targetWorkspaceId));
        return vo;
    }

    @Override
    public AuthVO refreshToken(String userId, String workspaceId, String email) {
        // 校验用户仍存在且状态正常
        SysUserDO user = userMapper.selectById(userId);
        if (user == null || !"active".equals(user.getStatus())) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        // P1: 刷新时重新校验 workspace 归属；若用户已被移出该工作空间则拒绝刷新
        if (!workspaceService.checkPermission(userId, workspaceId)) {
            throw new BusinessException(ResultCode.WORKSPACE_NO_PERMISSION);
        }
        // 重新签发 access + 滚动 refresh token
        String access = jwtUtil.generateToken(userId, workspaceId, email);
        String refresh = jwtUtil.generateRefreshToken(userId, workspaceId, email);

        AuthVO vo = new AuthVO();
        vo.setToken(access);
        vo.setRefreshToken(refresh);
        vo.setUserId(userId);
        vo.setEmail(email);
        vo.setWorkspaceId(workspaceId);
        vo.setWorkspaceName(workspaceService.getWorkspaceName(workspaceId));
        return vo;
    }

    /**
     * P2: 会话恢复（管理端点 /api/auth/me）时的轻量二次校验：
     * 重新核对账号状态（禁用）与 workspace 归属，避免 24h access token 期间账号被禁用仍可用。
     */
    @Override
    public void checkSession(String userId, String workspaceId) {
        SysUserDO user = userMapper.selectById(userId);
        if (user == null || !"active".equals(user.getStatus())) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!workspaceService.checkPermission(userId, workspaceId)) {
            throw new BusinessException(ResultCode.WORKSPACE_NO_PERMISSION);
        }
    }

    private void incrementFailCount(String failKey, String userId) {        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1) {
            redisTemplate.expire(failKey, lockMinutes, TimeUnit.MINUTES);
        }
        // 达到上限时，持久化锁定状态到 DB（Redis 不是唯一来源）
        if (count != null && count >= maxLoginFail && userId != null) {
            SysUserDO user = userMapper.selectById(userId);
            if (user != null) {
                user.setStatus("locked");
                user.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
                user.setLoginFailCount(count.intValue());
                user.setUpdatedAt(LocalDateTime.now());
                userMapper.updateById(user);
            }
        }
    }

    /**
     * Q6: 提取客户端真实 IP（支持反向代理 X-Forwarded-For），用于登录失败锁定 key 的 IP 维度隔离。
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                if (request.getRemoteAddr() != null) {
                    return request.getRemoteAddr();
                }
            }
        } catch (Exception e) {
            log.debug("获取客户端 IP 失败: {}", e.getMessage());
        }
        return "unknown";
    }
}
