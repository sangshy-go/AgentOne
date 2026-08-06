package com.agentone.knowledge.service;

import com.agentone.knowledge.dto.ModelDTO;
import com.agentone.knowledge.vo.ModelVO;
import com.agentone.common.result.PageResult;

import java.util.List;

/**
 * 模型 Service
 */
public interface ModelService {

    /**
     * 创建模型
     */
    ModelVO createModel(String providerId, ModelDTO dto);

    /**
     * 获取 provider 下的所有模型
     */
    PageResult<ModelVO> listModels(String providerId, Integer page, Integer size);

    /**
     * 获取模型详情
     */
    ModelVO getModel(String modelId);

    /**
     * 更新模型
     */
    ModelVO updateModel(String modelId, ModelDTO dto);

    /**
     * 删除模型
     */
    void deleteModel(String modelId);

    /**
     * 按类型获取模型列表（用于知识库选择 embedding 模型）
     */
    List<ModelVO> listModelsByType(String modelType);
}
