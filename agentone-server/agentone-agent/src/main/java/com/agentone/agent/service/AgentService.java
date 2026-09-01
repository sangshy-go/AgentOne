package com.agentone.agent.service;

import com.agentone.agent.dto.AgentDTO;
import com.agentone.agent.dto.AgentUpdateDTO;
import com.agentone.agent.vo.AgentVO;
import com.agentone.common.result.PageResult;


/**
 * Agent 管理服务
 */
public interface AgentService {

    PageResult<AgentVO> list(Integer page, Integer size);

    AgentVO getById(String id);

    AgentVO create(AgentDTO dto);

    AgentVO update(String id, AgentUpdateDTO dto);

    void delete(String id);

    /** 停用 Agent */
    AgentVO stop(String id);

    /** 退回草稿 */
    AgentVO revertToDraft(String id);
}
