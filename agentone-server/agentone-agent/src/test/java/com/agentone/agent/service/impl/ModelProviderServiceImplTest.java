package com.agentone.agent.service.impl;

import com.agentone.agent.mapper.AgentMapper;
import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.knowledge.entity.ModelDO;
import com.agentone.knowledge.entity.ModelProviderDO;
import com.agentone.knowledge.mapper.KnowledgeBaseMapper;
import com.agentone.knowledge.mapper.ModelMapper;
import com.agentone.knowledge.mapper.ModelProviderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ModelProviderServiceImpl 删除保护单测：
 * 供应商下存在被知识库绑定的模型时拒绝删除（6017），
 * 因为 model.provider_id 是 ON DELETE CASCADE，连级删除会让知识库留下死引用。
 */
@ExtendWith(MockitoExtension.class)
class ModelProviderServiceImplTest {

    @Mock
    private ModelProviderMapper modelProviderMapper;
    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock
    private AgentMapper agentMapper;
    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private ModelProviderServiceImpl service;

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
        return p;
    }

    @Test
    void delete_providerHasModelBoundToKnowledge_throws6017AndNoDelete() {
        when(modelProviderMapper.selectById("p-1")).thenReturn(provider());
        ModelDO m = new ModelDO();
        m.setId("m-1");
        when(modelMapper.selectList(any())).thenReturn(List.of(m));
        when(knowledgeBaseMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.delete("p-1"));
        assertEquals(6017, ex.getCode());
        verify(modelProviderMapper, never()).deleteById(anyString());
    }

    @Test
    void delete_noModels_deletesAndClearsRefs() {
        when(modelProviderMapper.selectById("p-1")).thenReturn(provider());
        when(modelMapper.selectList(any())).thenReturn(List.of());

        service.delete("p-1");

        verify(knowledgeBaseMapper).clearModelProviderId("p-1");
        verify(agentMapper).clearModelProviderId("p-1");
        verify(modelProviderMapper).deleteById("p-1");
    }
}
