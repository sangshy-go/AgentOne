package com.agentone.apikey.mapper;

import com.agentone.apikey.entity.ApiKeyDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * API Key Mapper
 */
@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKeyDO> {

    /**
     * 按 keyHash 查找 API Key（跳过租户过滤，因为此时尚无 workspace 上下文）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM api_key WHERE key_hash = #{keyHash} AND status = 'active' LIMIT 1")
    ApiKeyDO selectByKeyHash(@Param("keyHash") String keyHash);
}
