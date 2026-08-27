package com.agentone.im.mapper;

import com.agentone.im.entity.ImBotDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.io.Serializable;

@Mapper
public interface ImBotMapper extends BaseMapper<ImBotDO> {

    /**
     * 回调路径专用：平台回调无 JWT/用户上下文，无法注入 workspace_id，
     * 鉴权由平台签名机制保证（见 ImCallbackController），botId 本身即访问凭证。
     * 管理端 CRUD 一律走带 workspace_id 条件的 wrapper 查询（自动租户过滤仍生效）。
     */
    @Override
    @InterceptorIgnore(tenantLine = "true")
    ImBotDO selectById(Serializable id);
}
