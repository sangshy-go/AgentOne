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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelVO createModel(String providerId, ModelDTO dto) {
        // 校验 provider 存在
        ModelProviderDO provider = providerMapper.selectById(providerId);
        if (provider == null) {
            throw new BusinessException(6001, "模型供应商不存在");
        }

        ModelDO model = new ModelDO();
        model.setProviderId(providerId);
        model.setModelType(dto.getModelType());
        model.setModelId(dto.getModelId());
        model.setDisplayName(dto.getDisplayName() != null ? dto.getDisplayName() : dto.getModelId());
        model.setContextSize(dto.getContextSize());
        model.setMaxTokens(dto.getMaxTokens());
        model.setDimensions(dto.getDimensions());
        model.setStatus("active");
        model.setCreatedAt(LocalDateTime.now());
        model.setUpdatedAt(LocalDateTime.now());

        modelMapper.insert(model);
        log.info("创建模型成功: providerId={}, modelType={}, modelId={}, dimensions={}",
                providerId, dto.getModelType(), dto.getModelId(), dto.getDimensions());

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

        model.setModelType(dto.getModelType());
        model.setModelId(dto.getModelId());
        model.setDisplayName(dto.getDisplayName() != null ? dto.getDisplayName() : dto.getModelId());
        model.setContextSize(dto.getContextSize());
        model.setMaxTokens(dto.getMaxTokens());
        if (dto.getDimensions() != null) {
            model.setDimensions(dto.getDimensions());
        }
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

    private ModelVO toModelVO(ModelDO model, String providerName) {
        ModelVO vo = new ModelVO();
        BeanUtils.copyProperties(model, vo);
        vo.setProviderName(providerName);
        return vo;
    }
}
