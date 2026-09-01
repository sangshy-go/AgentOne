package com.agentone.knowledge.service.impl;

import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.knowledge.dto.ModelDTO;
import com.agentone.knowledge.entity.ModelDO;
import com.agentone.knowledge.entity.ModelProviderDO;
import com.agentone.knowledge.mapper.KnowledgeBaseMapper;
import com.agentone.knowledge.mapper.ModelMapper;
import com.agentone.knowledge.mapper.ModelProviderMapper;
import com.agentone.knowledge.vector.VectorStoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ModelServiceImpl 的 embedding 模型配置校验单测：
 * 创建时探测失败 6015 / 创建成功落维度 / chat 不探测；
 * 更新时已绑定知识库拒绝改关键字段 6016 / 未绑定改 modelId 重探测（失败 6015 / 成功回写新维度）。
 */
@ExtendWith(MockitoExtension.class)
class ModelServiceImplTest {

    @Mock
    private ModelMapper modelMapper;
    @Mock
    private ModelProviderMapper providerMapper;
    @Mock
    private VectorStoreService vectorStoreService;
    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @InjectMocks
    private ModelServiceImpl service;

    @BeforeEach
    void setUp() {
        RuntimeContext.set(Context.of("user-1", "ws-1"));
    }

    @AfterEach
    void tearDown() {
        RuntimeContext.clear();
    }

    private ModelProviderDO provider() {
        ModelProviderDO p = new ModelProviderDO();
        p.setId("p-1");
        p.setWorkspaceId("ws-1");
        p.setName("Qwen");
        p.setProvider("dashscope");
        p.setApiKey("sk-test");
        p.setBaseUrl("https://example.com/compatible-mode/v1");
        return p;
    }

    private ModelDTO dto(String type, String modelId) {
        ModelDTO dto = new ModelDTO();
        dto.setModelType(type);
        dto.setModelId(modelId);
        return dto;
    }

    private ModelDO existingEmbeddingModel() {
        ModelDO m = new ModelDO();
        m.setId("m-1");
        m.setProviderId("p-1");
        m.setModelType("embedding");
        m.setModelId("old-embedding");
        m.setDimensions(1024);
        m.setStatus("active");
        return m;
    }

    @Test
    void createModel_embeddingProbeFails_throws6015AndNoInsert() {
        when(providerMapper.selectById("p-1")).thenReturn(provider());
        when(vectorStoreService.probeEmbeddingDimensions(any(), any()))
                .thenThrow(new RuntimeException("400 - Model not exist."));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createModel("p-1", dto("embedding", "bad-model")));
        assertEquals(6015, ex.getCode());
        assertTrue(ex.getMessage().contains("Model not exist"));
        verify(modelMapper, never()).insert(any(ModelDO.class));
    }

    @Test
    void createModel_embeddingProbeSucceeds_persistsDimensions() {
        when(providerMapper.selectById("p-1")).thenReturn(provider());
        when(vectorStoreService.probeEmbeddingDimensions(any(), any())).thenReturn(1024);

        service.createModel("p-1", dto("embedding", "text-embedding-v3"));

        ArgumentCaptor<ModelDO> captor = ArgumentCaptor.forClass(ModelDO.class);
        verify(modelMapper).insert(captor.capture());
        assertEquals(1024, captor.getValue().getDimensions());
    }

    @Test
    void createModel_chatType_skipsProbe() {
        when(providerMapper.selectById("p-1")).thenReturn(provider());

        service.createModel("p-1", dto("chat", "qwen-plus"));

        verify(vectorStoreService, never()).probeEmbeddingDimensions(any(), any());
        ArgumentCaptor<ModelDO> captor = ArgumentCaptor.forClass(ModelDO.class);
        verify(modelMapper).insert(captor.capture());
        assertNull(captor.getValue().getDimensions());
    }

    @Test
    void updateModel_boundEmbeddingModelIdChange_throws6016() {
        when(modelMapper.selectById("m-1")).thenReturn(existingEmbeddingModel());
        when(providerMapper.selectById("p-1")).thenReturn(provider());
        when(knowledgeBaseMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateModel("m-1", dto("embedding", "new-embedding")));
        assertEquals(6016, ex.getCode());
        verify(modelMapper, never()).updateById(any(ModelDO.class));
    }

    @Test
    void updateModel_unboundEmbeddingModelIdChangeReprobeFails_throws6015AndNoUpdate() {
        when(modelMapper.selectById("m-1")).thenReturn(existingEmbeddingModel());
        when(providerMapper.selectById("p-1")).thenReturn(provider());
        when(knowledgeBaseMapper.selectCount(any())).thenReturn(0L);
        when(vectorStoreService.probeEmbeddingDimensions(any(), any()))
                .thenThrow(new RuntimeException("400 - Model not exist."));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateModel("m-1", dto("embedding", "bad-model")));
        assertEquals(6015, ex.getCode());
        verify(modelMapper, never()).updateById(any(ModelDO.class));
    }

    @Test
    void deleteModel_boundEmbedding_throws6016AndNoDelete() {
        when(modelMapper.selectById("m-1")).thenReturn(existingEmbeddingModel());
        when(providerMapper.selectById("p-1")).thenReturn(provider());
        when(knowledgeBaseMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deleteModel("m-1"));
        assertEquals(6016, ex.getCode());
        verify(modelMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void deleteModel_unboundEmbedding_deletes() {
        when(modelMapper.selectById("m-1")).thenReturn(existingEmbeddingModel());
        when(providerMapper.selectById("p-1")).thenReturn(provider());
        when(knowledgeBaseMapper.selectCount(any())).thenReturn(0L);

        service.deleteModel("m-1");

        verify(modelMapper).deleteById((java.io.Serializable) "m-1");
    }

    @Test
    void updateModel_unboundEmbeddingModelIdChangeReprobeSucceeds_updatesDimensions() {
        when(modelMapper.selectById("m-1")).thenReturn(existingEmbeddingModel());
        when(providerMapper.selectById("p-1")).thenReturn(provider());
        when(knowledgeBaseMapper.selectCount(any())).thenReturn(0L);
        when(vectorStoreService.probeEmbeddingDimensions(any(), any())).thenReturn(768);

        service.updateModel("m-1", dto("embedding", "new-embedding"));

        ArgumentCaptor<ModelDO> captor = ArgumentCaptor.forClass(ModelDO.class);
        verify(modelMapper).updateById(captor.capture());
        assertEquals(768, captor.getValue().getDimensions());
    }
}
