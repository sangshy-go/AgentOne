package com.agentone.agent.service;

import com.agentone.agent.dto.ModelCheckDTO;
import com.agentone.agent.vo.ModelCheckVO;

/**
 * 模型检测服务
 */
public interface ModelCheckService {

    /**
     * 检测模型可用性
     */
    ModelCheckVO checkModel(ModelCheckDTO dto);
}
