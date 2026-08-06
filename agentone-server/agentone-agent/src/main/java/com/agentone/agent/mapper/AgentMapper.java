package com.agentone.agent.mapper;

import com.agentone.agent.entity.AgentDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AgentMapper extends BaseMapper<AgentDO> {

    /**
     * 清除对指定模型供应商的引用（跨租户，用于供应商删除时解除 FK 约束）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE agent SET model_provider_id = NULL WHERE model_provider_id = #{providerId}")
    int clearModelProviderId(@Param("providerId") String providerId);
}
