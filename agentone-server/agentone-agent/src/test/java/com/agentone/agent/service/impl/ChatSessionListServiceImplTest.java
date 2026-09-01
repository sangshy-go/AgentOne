package com.agentone.agent.service.impl;

import com.agentone.agent.config.AgentScopeConfig;
import com.agentone.agent.entity.ChatSessionDO;
import com.agentone.agent.mapper.AgentMapper;
import com.agentone.agent.mapper.ChatAttachmentMapper;
import com.agentone.agent.mapper.ChatMessageMapper;
import com.agentone.agent.mapper.ChatSessionMapper;
import com.agentone.agent.memory.MemoryService;
import com.agentone.agent.persona.VariableInjector;
import com.agentone.agent.service.ChatAttachmentService;
import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.knowledge.mapper.ModelMapper;
import com.agentone.knowledge.mapper.ModelProviderMapper;
import com.agentone.knowledge.service.KnowledgeService;
import com.agentone.skill.core.SkillCallLogRecorder;
import com.agentone.skill.core.SkillRegistry;
import com.agentone.skill.service.AgentSkillService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.Model;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话列表隔离：listSessions 必须按当前用户过滤。
 * 回归背景：IM 回调虚拟用户（im:platform:sender）的会话曾泄漏到控制台列表，
 * 抽屉默认选中后加载消息/发送被归属校验 4003 拒绝，表现为「已发布 Agent 无法对话」。
 */
@ExtendWith(MockitoExtension.class)
class ChatSessionListServiceImplTest {

    @Mock private AgentMapper agentMapper;
    @Mock private ChatSessionMapper sessionMapper;
    @Mock private ChatMessageMapper messageMapper;
    @Mock private VariableInjector variableInjector;
    @Mock private MemoryService memoryService;
    @Mock private AgentSkillService agentSkillService;
    @Mock private SkillRegistry skillRegistry;
    @Mock private SkillCallLogRecorder skillCallLogRecorder;
    @Mock private KnowledgeService knowledgeService;
    @Mock private ModelMapper modelMapper;
    @Mock private ModelProviderMapper modelProviderMapper;
    @Mock private Model model;
    @Mock private ObjectMapper objectMapper;
    @Mock private AgentScopeConfig agentScopeConfig;
    @Mock private ChatAttachmentService attachmentService;
    @Mock private ChatAttachmentMapper attachmentMapper;

    @InjectMocks
    private ChatServiceImpl chatService;

    @BeforeAll
    static void initTableInfo() {
        // 单测环境无 MyBatis 容器，手动初始化实体元数据供 LambdaQueryWrapper 列解析
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ChatSessionDO.class);
    }

    @BeforeEach
    void setUp() {
        RuntimeContext.set(Context.of("user-1", "ws-1"));
    }

    @AfterEach
    void tearDown() {
        RuntimeContext.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listSessions_存在他人及IM虚拟用户会话_查询条件必须包含当前用户过滤() {
        when(sessionMapper.selectPage(any(Page.class), any())).thenAnswer(inv -> inv.getArgument(0));

        chatService.listSessions("agent-1", 1, 20);

        ArgumentCaptor<LambdaQueryWrapper<ChatSessionDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sessionMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("user_id"), "会话列表必须按当前用户过滤，防止跨用户/IM 虚拟用户会话泄漏");
        assertTrue(sql.contains("agent_id"));
        assertTrue(sql.contains("workspace_id"));
        Collection<Object> params = captor.getValue().getParamNameValuePairs().values();
        assertTrue(params.contains("user-1"), "user_id 条件必须绑定当前登录用户");
        assertTrue(params.contains("ws-1"));
        assertTrue(params.contains("agent-1"));
    }
}
