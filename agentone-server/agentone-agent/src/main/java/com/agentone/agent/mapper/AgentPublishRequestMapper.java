package com.agentone.agent.mapper;

import com.agentone.agent.entity.AgentPublishRequestDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 发布审批申请单 Mapper
 */
@Mapper
public interface AgentPublishRequestMapper extends BaseMapper<AgentPublishRequestDO> {
}
