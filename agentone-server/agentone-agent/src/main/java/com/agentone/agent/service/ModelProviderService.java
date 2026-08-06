package com.agentone.agent.service;

import com.agentone.agent.dto.ModelProviderDTO;
import com.agentone.agent.vo.ModelCheckVO;
import com.agentone.agent.vo.ModelProviderVO;
import com.agentone.common.result.PageResult;

import java.util.List;

/**
 * 模型供应商管理服务
 */
public interface ModelProviderService {

    ModelProviderVO create(ModelProviderDTO dto);

    PageResult<ModelProviderVO> list(Integer page, Integer size);

    ModelProviderVO getById(String id);

    ModelProviderVO update(String id, ModelProviderDTO dto);

    void delete(String id);

    ModelCheckVO check(String id);
}
