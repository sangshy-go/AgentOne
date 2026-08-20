package com.agentone.knowledge.service.impl;

import com.agentone.common.exception.BusinessException;
import com.agentone.knowledge.dto.ModelDTO;
import com.agentone.knowledge.entity.ModelDO;
import com.agentone.knowledge.entity.ModelProviderDO;
import com.agentone.knowledge.mapper.ModelMapper;
import com.agentone.knowledge.mapper.ModelProviderMapper;
import com.agentone.knowledge.service.ModelService;
import com.agentone.knowledge.vo.ModelVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.agentone.common.result.PageResult;
import com.agentone.common.context.RuntimeContext;
import com.agentone.knowledge.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelServiceImpl implements ModelService {

    private final ModelMapper modelMapper;
    private final ModelProviderMapper providerMapper;
    private final VectorStoreService vectorStoreService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelVO createModel(String providerId, ModelDTO dto) {
        // 校验 provider 存在
        ModelProviderDO provider = providerMapper.selectById(providerId);
        if (provider == null) {
            throw new BusinessException(6001, "模型供应商不存在");
        }

        // S9: 校验 provider 归属当前工作空间（model_provider 不在租户白名单，需手动校验）
        String wsId = RuntimeContext.getWorkspaceId();
        if (wsId != null && provider.getWorkspaceId() != null
                && !wsId.equals(provider.getWorkspaceId())) {
            throw new BusinessException(6013, "无权在该模型供应商下创建模型");
        }

        ModelDO model = new ModelDO();
        model.setProviderId(providerId);
        model.setModelType(dto.getModelType());
        model.setModelId(dto.getModelId());
        model.setDisplayName(dto.getDisplayName() != null ? dto.getDisplayName() : dto.getModelId());
        model.setContextSize(dto.getContextSize());
        model.setMaxTokens(dto.getMaxTokens());
        // D1: 不持久化用户传入的未校验 dimensions——错误的维度会让该模型后续所有文档向量化失败。
        // embedding 类型在创建时主动探测真实维度并写入；其它类型维度恒为 null（由系统首次使用时解析）。
        if ("embedding".equals(dto.getModelType())) {
            try {
                int dims = vectorStoreService.probeEmbeddingDimensions(provider, model);
                model.setDimensions(dims);
            } catch (Exception e) {
                log.warn("创建 embedding 模型时探测维度失败，留待首次使用时解析: providerId={}, modelId={}, error={}",
                        providerId, dto.getModelId(), e.getMessage());
            }
        }
        model.setStatus("active");
        model.setCreatedAt(LocalDateTime.now());
        model.setUpdatedAt(LocalDateTime.now());

        modelMapper.insert(model);
        log.info("创建模型成功: providerId={}, modelType={}, modelId={}, dimensions={}",
                providerId, dto.getModelType(), dto.getModelId(), model.getDimensions());

        return toModelVO(model, provider.getName());
    }

    @Override
    public PageResult<ModelVO> listModels(String providerId, Integer page, Integer size) {
        Page<ModelDO> p = new Page<>(page, size);
        Page<ModelDO> result = modelMapper.selectPage(p,
                new LambdaQueryWrapper<ModelDO>()
                        .eq(ModelDO::getProviderId, providerId)
                        .orderByDesc(ModelDO::getCreatedAt));

        // 查询 provider 名称
        ModelProviderDO provider = providerMapper.selectById(providerId);
        String providerName = provider != null ? provider.getName() : null;

        Page<ModelVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(m -> toModelVO(m, providerName))
                .collect(Collectors.toList()));
        return PageResult.of(voPage);
    }

    @Override
    public ModelVO getModel(String modelId) {
        ModelDO model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException(6002, "模型不存在");
        }

        ModelProviderDO provider = providerMapper.selectById(model.getProviderId());
        String providerName = provider != null ? provider.getName() : null;

        return toModelVO(model, providerName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelVO updateModel(String modelId, ModelDTO dto) {
        ModelDO model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException(6002, "模型不存在");
        }

        // S9: 校验模型所属 provider 归属当前工作空间（model_provider 不在租户白名单，需手动校验）
        verifyProviderOwnership(model.getProviderId());

        model.setModelType(dto.getModelType());
        model.setModelId(dto.getModelId());
        model.setDisplayName(dto.getDisplayName() != null ? dto.getDisplayName() : dto.getModelId());
        model.setContextSize(dto.getContextSize());
        model.setMaxTokens(dto.getMaxTokens());
        // dimensions 为系统首次使用时自动探测的只读字段，更新时忽略客户端传入值：
        // embedding 模型维度决定向量表（vector_{dimensions}），若在更新时篡改，已入库向量与
        // 后续查询向量将处于不同维度空间，导致检索失效、历史向量沦为孤儿。保持与创建时 /
        // 自动探测值一致，如需更换维度请新建模型并在知识库侧重新绑定（知识库 embedding 模型本身不可变）。
        model.setUpdatedAt(LocalDateTime.now());

        modelMapper.updateById(model);

        ModelProviderDO provider = providerMapper.selectById(model.getProviderId());
        String providerName = provider != null ? provider.getName() : null;

        return toModelVO(model, providerName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(String modelId) {
        ModelDO model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException(6002, "模型不存在");
        }

        // S9: 校验模型所属 provider 归属当前工作空间（model_provider 不在租户白名单，需手动校验）
        verifyProviderOwnership(model.getProviderId());

        modelMapper.deleteById(modelId);
        log.info("删除模型成功: modelId={}", modelId);
    }

    @Override
    public List<ModelVO> listModelsByType(String modelType) {
        // 先查当前 workspace 下的 provider IDs（model 表无 workspace_id，需要通过 provider 过滤）
        String wsId = com.agentone.common.context.RuntimeContext.getWorkspaceId();
        List<ModelProviderDO> wsProviders = providerMapper.selectList(
                new LambdaQueryWrapper<ModelProviderDO>()
                        .eq(ModelProviderDO::getWorkspaceId, wsId)
        );
        List<String> providerIds = wsProviders.stream()
                .map(ModelProviderDO::getId)
                .collect(Collectors.toList());

        if (providerIds.isEmpty()) {
            return List.of();
        }

        List<ModelDO> models = modelMapper.selectList(
                new LambdaQueryWrapper<ModelDO>()
                        .eq(ModelDO::getModelType, modelType)
                        .eq(ModelDO::getStatus, "active")
                        .in(ModelDO::getProviderId, providerIds)
                        .orderByDesc(ModelDO::getCreatedAt)
        );

        return models.stream()
                .map(m -> {
                    String providerName = wsProviders.stream()
                            .filter(p -> p.getId().equals(m.getProviderId()))
                            .map(ModelProviderDO::getName)
                            .findFirst().orElse(null);
                    return toModelVO(m, providerName);
                })
                .collect(Collectors.toList());
    }

    /**
     * S9: 校验模型所属 provider 归属于当前工作空间。
     * model / model_provider 是白名单表，租户拦截器不会自动过滤，必须手动校验归属，
     * 否则可越权操作他人空间的模型。
     */
    private void verifyProviderOwnership(String providerId) {
        ModelProviderDO provider = providerMapper.selectById(providerId);
        String wsId = RuntimeContext.getWorkspaceId();
        if (provider == null || (wsId != null && provider.getWorkspaceId() != null
                && !wsId.equals(provider.getWorkspaceId()))) {
            throw new BusinessException(6013, "无权操作该模型（供应商不存在或不属于当前工作空间）");
        }
    }

    private ModelVO toModelVO(ModelDO model, String providerName) {
        ModelVO vo = new ModelVO();
        BeanUtils.copyProperties(model, vo);
        vo.setProviderName(providerName);
        return vo;
    }
}
