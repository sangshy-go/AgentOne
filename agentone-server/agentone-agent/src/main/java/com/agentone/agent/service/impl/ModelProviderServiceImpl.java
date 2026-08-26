package com.agentone.agent.service.impl;

import com.agentone.agent.dto.ModelProviderDTO;
import com.agentone.agent.mapper.AgentMapper;
import com.agentone.agent.service.ModelProviderService;
import com.agentone.agent.util.BaseUrlValidator;
import com.agentone.agent.vo.ModelCheckVO;
import com.agentone.agent.vo.ModelProviderVO;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.knowledge.entity.KnowledgeBaseDO;
import com.agentone.knowledge.entity.ModelDO;
import com.agentone.knowledge.entity.ModelProviderDO;
import com.agentone.knowledge.mapper.KnowledgeBaseMapper;
import com.agentone.knowledge.mapper.ModelMapper;
import com.agentone.knowledge.mapper.ModelProviderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.agentone.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型供应商管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelProviderServiceImpl implements ModelProviderService {

    private final ModelProviderMapper modelProviderMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final AgentMapper agentMapper;
    private final ModelMapper modelMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelProviderVO create(ModelProviderDTO dto) {
        // create 场景必填校验（DTO 上不加 @NotBlank 以兼容 update 的可选字段）
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException(7002, "供应商名称不能为空");
        }
        if (dto.getProvider() == null || dto.getProvider().isBlank()) {
            throw new BusinessException(7003, "供应商类型不能为空");
        }
        if (dto.getApiKey() == null || dto.getApiKey().isBlank()) {
            throw new BusinessException(7004, "API Key 不能为空");
        }
        if (dto.getBaseUrl() == null || dto.getBaseUrl().isBlank()) {
            throw new BusinessException(7005, "Base URL 不能为空");
        }

        ModelProviderDO provider = new ModelProviderDO();
        provider.setWorkspaceId(RuntimeContext.getWorkspaceId());
        provider.setName(dto.getName());
        provider.setProvider(dto.getProvider());
        provider.setApiKey(dto.getApiKey());
        provider.setBaseUrl(dto.getBaseUrl());
        provider.setStatus("active");
        provider.setCreatedAt(LocalDateTime.now());
        provider.setUpdatedAt(LocalDateTime.now());

        modelProviderMapper.insert(provider);
        return toVO(provider);
    }

    @Override
    public PageResult<ModelProviderVO> list(Integer page, Integer size) {
        String wsId = RuntimeContext.getWorkspaceId();
        Page<ModelProviderDO> p = new Page<>(page, size);
        Page<ModelProviderDO> result = modelProviderMapper.selectPage(p,
                new LambdaQueryWrapper<ModelProviderDO>()
                        .eq(ModelProviderDO::getWorkspaceId, wsId)
                        .orderByDesc(ModelProviderDO::getCreatedAt));
        Page<ModelProviderVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return PageResult.of(voPage);
    }

    @Override
    public ModelProviderVO getById(String id) {
        ModelProviderDO provider = modelProviderMapper.selectById(id);
        if (provider == null) {
            throw new BusinessException(7001, "模型供应商不存在");
        }
        checkOwnership(provider);
        return toVO(provider);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelProviderVO update(String id, ModelProviderDTO dto) {
        ModelProviderDO provider = modelProviderMapper.selectById(id);
        if (provider == null) {
            throw new BusinessException(7001, "模型供应商不存在");
        }
        checkOwnership(provider);
        if (dto.getName() != null) provider.setName(dto.getName());
        if (dto.getProvider() != null) provider.setProvider(dto.getProvider());
        if (dto.getApiKey() != null) provider.setApiKey(dto.getApiKey());
        if (dto.getBaseUrl() != null) provider.setBaseUrl(dto.getBaseUrl());
        provider.setUpdatedAt(LocalDateTime.now());

        modelProviderMapper.updateById(provider);
        return toVO(provider);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        ModelProviderDO provider = modelProviderMapper.selectById(id);
        if (provider == null) {
            throw new BusinessException(7001, "模型供应商不存在");
        }
        checkOwnership(provider);

        // model.provider_id 是 ON DELETE CASCADE：删供应商会连级删掉其下模型，
        // 而 knowledge_base.embedding_model_id 无外键，被绑定的知识库会留下死引用永久不可用，
        // 故存在被绑定的模型时拒绝删除（与 deleteModel 的绑定保护一致）
        List<String> modelIds = modelMapper.selectList(
                new LambdaQueryWrapper<ModelDO>()
                        .eq(ModelDO::getProviderId, id))
                .stream().map(ModelDO::getId).collect(Collectors.toList());
        if (!modelIds.isEmpty()) {
            Long bound = knowledgeBaseMapper.selectCount(
                    new LambdaQueryWrapper<KnowledgeBaseDO>()
                            .in(KnowledgeBaseDO::getEmbeddingModelId, modelIds));
            if (bound != null && bound > 0) {
                throw new BusinessException(6017, "该供应商下仍有 Embedding 模型被知识库绑定，不能删除；请先删除依赖它的知识库");
            }
        }

        // 解除知识库 / Agent 对该供应商的引用（跨租户，避免多租户拦截器漏清导致 FK 冲突）
        int kbCleared = knowledgeBaseMapper.clearModelProviderId(id);
        int agentCleared = agentMapper.clearModelProviderId(id);
        log.info("删除供应商 {}: 解除 {} 个知识库、{} 个 Agent 的引用", id, kbCleared, agentCleared);
        modelProviderMapper.deleteById(id);
    }

    @Override
    public ModelCheckVO check(String id) {
        ModelProviderDO provider = modelProviderMapper.selectById(id);
        if (provider == null) {
            throw new BusinessException(7001, "模型供应商不存在");
        }
        checkOwnership(provider);

        // SSRF 防护（Bug7）：baseUrl 用户可控，发起请求前校验，
        // 拒绝内网/回环/链路本地/元数据地址。置于 try 外，让 6006 以明确错误码返回，
        // 而不是被吞成"检测失败"的连通性结果。
        BaseUrlValidator.validateForSsrf(provider.getBaseUrl());

        long start = System.currentTimeMillis();
        ModelCheckVO vo = new ModelCheckVO();

        try {
            // 连通性检测：调用 /models 端点验证 API Key 是否有效
            String modelsUrl = provider.getBaseUrl().replaceAll("/+$", "") + "/models";
            // 设置连接/读取超时，避免对黑洞地址无限挂起（Bug2）
            org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                    new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5000);
            factory.setReadTimeout(10000);
            org.springframework.web.client.RestTemplate rest =
                    new org.springframework.web.client.RestTemplate(factory);
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(provider.getApiKey());
            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
            rest.exchange(modelsUrl, org.springframework.http.HttpMethod.GET, entity, String.class);

            vo.setAvailable(true);
            vo.setMessage("供应商连接正常");
            vo.setLatencyMs(System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("供应商连通性检测失败: providerId={}, error={}", id, e.getMessage());
            vo.setAvailable(false);
            vo.setMessage("检测失败: " + e.getMessage());
            vo.setLatencyMs(System.currentTimeMillis() - start);
        }

        return vo;
    }

    /**
     * 获取原始 ModelProviderDO（内部使用，不脱敏 API Key）
     * 供 VectorStoreService 等需要实际 API Key 的模块调用
     * 注意：调用方须自行校验 workspaceId 归属
     */
    public ModelProviderDO getRawById(String id) {
        return modelProviderMapper.selectById(id);
    }

    /**
     * 校验模型供应商归属当前工作空间，防止跨租户越权
     */
    private void checkOwnership(ModelProviderDO provider) {
        String currentWsId = RuntimeContext.getWorkspaceId();
        if (currentWsId == null || !currentWsId.equals(provider.getWorkspaceId())) {
            throw new BusinessException(6012, "无权访问该模型供应商");
        }
    }

    private ModelProviderVO toVO(ModelProviderDO provider) {
        ModelProviderVO vo = new ModelProviderVO();
        BeanUtils.copyProperties(provider, vo);
        vo.setApiKey(maskApiKey(provider.getApiKey()));
        return vo;
    }

    /**
     * API Key 脱敏：sk-****xxxx（只显示前3位和后4位）
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 3) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
