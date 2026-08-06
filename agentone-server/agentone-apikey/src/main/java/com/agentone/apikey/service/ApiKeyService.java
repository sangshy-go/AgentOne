package com.agentone.apikey.service;

import com.agentone.apikey.dto.CreateApiKeyDTO;
import com.agentone.apikey.entity.ApiKeyDO;
import com.agentone.apikey.vo.ApiKeyVO;
import com.agentone.apikey.vo.CreateApiKeyVO;
import com.agentone.common.result.PageResult;

import java.util.List;

/**
 * API Key 管理服务
 */
public interface ApiKeyService {

    /**
     * 创建 API Key
     * @return 包含明文 Key 的 VO（仅创建时返回一次）
     */
    CreateApiKeyVO create(String workspaceId, CreateApiKeyDTO dto);

    /**
     * 查询工作空间下的 API Key 列表
     */
    PageResult<ApiKeyVO> list(String workspaceId, Integer page, Integer size);

    /**
     * 停用 API Key（软删除）
     */
    void disable(String workspaceId, String id);

    /**
     * 根据明文 Key 校验并返回实体
     */
    ApiKeyDO validate(String rawKey);

    /**
     * 校验调用次数是否超限
     */
    boolean checkDailyLimit(ApiKeyDO apiKey);

    /**
     * 增加调用计数
     */
    void incrementUsage(ApiKeyDO apiKey);
}
