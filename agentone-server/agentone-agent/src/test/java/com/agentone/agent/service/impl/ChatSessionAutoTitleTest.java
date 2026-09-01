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
import com.agentone.knowledge.mapper.ModelMapper;
import com.agentone.knowledge.mapper.ModelProviderMapper;
import com.agentone.knowledge.service.KnowledgeService;
import com.agentone.skill.core.SkillCallLogRecorder;
import com.agentone.skill.core.SkillRegistry;
import com.agentone.skill.service.AgentSkillService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.Model;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 会话自动标题：首条消息用任务描述替代默认「新对话」。
 * 覆盖派生规则（截断/空白压缩/纯附件回退）与防覆盖门禁（已有轮次/人工重命名）。
 */
@ExtendWith(MockitoExtension.class)
class ChatSessionAutoTitleTest {

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
        // 单测环境无 MyBatis 容器，手动初始化实体元数据供 LambdaUpdateWrapper 列解析
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ChatSessionDO.class);
    }

    private ChatSessionDO newSession(String title, int messageCount) {
        ChatSessionDO session = new ChatSessionDO();
        session.setId("session-1");
        session.setTitle(title);
        session.setMessageCount(messageCount);
        return session;
    }

    @Test
    void autoTitleSession_首条普通文本消息_标题取自消息内容() {
        ChatSessionDO session = newSession("新对话", 0);

        chatService.autoTitleSession(session, "帮我查一下今天的天气", null);

        assertEquals("帮我查一下今天的天气", session.getTitle());
        assertPersistedTitle("帮我查一下今天的天气");
    }

    @Test
    void autoTitleSession_消息超过30字符_截断并加省略号() {
        ChatSessionDO session = newSession("新对话", 0);
        String longText = "这是一个非常长的任务描述用来验证标题截断逻辑是否按预期工作不会把整段话都塞进标题里";

        chatService.autoTitleSession(session, longText, null);

        assertEquals(longText.substring(0, 30) + "…", session.getTitle());
        assertEquals(31, session.getTitle().length());
    }

    @Test
    void autoTitleSession_多行带冗余空白文本_压缩为单行标题() {
        ChatSessionDO session = newSession("新对话", 0);

        chatService.autoTitleSession(session, "  帮我写一份周报\n总结   本周进展  ", null);

        assertEquals("帮我写一份周报 总结 本周进展", session.getTitle());
    }

    @Test
    void autoTitleSession_纯附件无文本_标题用首个附件文件名() {
        ChatSessionDO session = newSession("新对话", 0);

        chatService.autoTitleSession(session, "", "合同扫描件.pdf");

        assertEquals("附件：合同扫描件.pdf", session.getTitle());
    }

    @Test
    void autoTitleSession_会话已有对话轮次_不覆盖既有标题() {
        ChatSessionDO session = newSession("新对话", 2);

        chatService.autoTitleSession(session, "第二轮消息", null);

        assertEquals("新对话", session.getTitle());
        verifyNoInteractions(sessionMapper);
    }

    @Test
    void autoTitleSession_标题已被人工重命名_不覆盖() {
        ChatSessionDO session = newSession("客户投诉处理-张先生", 0);

        chatService.autoTitleSession(session, "新的消息内容", null);

        assertEquals("客户投诉处理-张先生", session.getTitle());
        verifyNoInteractions(sessionMapper);
    }

    @Test
    void autoTitleSession_空白消息且无附件_不更新标题() {
        ChatSessionDO session = newSession("新对话", 0);

        chatService.autoTitleSession(session, "   ", null);

        assertEquals("新对话", session.getTitle());
        verifyNoInteractions(sessionMapper);
    }

    @SuppressWarnings("unchecked")
    private void assertPersistedTitle(String expectedTitle) {
        ArgumentCaptor<LambdaUpdateWrapper<ChatSessionDO>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(sessionMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<ChatSessionDO> wrapper = captor.getValue();
        assertTrue(wrapper.getSqlSet().contains("title"), "必须只更新 title 列");
        assertTrue(wrapper.getSqlSegment().contains("id"), "更新条件必须限定会话 id");
        Collection<Object> params = wrapper.getParamNameValuePairs().values();
        assertTrue(params.contains(expectedTitle), "title 参数必须绑定派生出的标题");
        assertTrue(params.contains("session-1"), "更新条件必须绑定会话 id 值");
    }
}
