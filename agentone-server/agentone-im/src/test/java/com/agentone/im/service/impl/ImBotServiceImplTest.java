package com.agentone.im.service.impl;

import com.agentone.agent.entity.AgentDO;
import com.agentone.agent.enums.AgentStatus;
import com.agentone.agent.mapper.AgentMapper;
import com.agentone.agent.service.ChatService;
import com.agentone.agent.dto.ChatRequestDTO;
import com.agentone.agent.vo.ChatResponseVO;
import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import com.agentone.common.exception.BusinessException;
import com.agentone.im.adapter.DingTalkSender;
import com.agentone.im.adapter.ImIncoming;
import com.agentone.im.crypto.ImConfigCrypto;
import com.agentone.im.dto.ImBotCreateDTO;
import com.agentone.im.dto.ImSendDTO;
import com.agentone.im.entity.ImBotDO;
import com.agentone.im.entity.ImSenderSessionDO;
import com.agentone.im.mapper.ImBotMapper;
import com.agentone.im.mapper.ImSenderSessionMapper;
import com.agentone.im.vo.ImBotVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.KeyGenerator;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ImBotServiceImpl 业务规则单测（课题⑤）：
 * 平台/模式组合校验 5202、SSRF 防护、凭证加密与掩码、发送门禁 5202/5206、
 * 回调入口 handleIncoming 的机器人/Agent 门禁与发送者会话映射
 */
@ExtendWith(MockitoExtension.class)
class ImBotServiceImplTest {

    private static final String DINGTALK_URL = "https://oapi.dingtalk.com/robot/send?access_token=tok123";

    @Mock
    private ImBotMapper imBotMapper;
    @Mock
    private ImSenderSessionMapper senderSessionMapper;
    @Mock
    private AgentMapper agentMapper;
    @Mock
    private ChatService chatService;
    @Mock
    private DingTalkSender dingTalkSender;

    private ImConfigCrypto crypto;
    private ImBotServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        crypto = new ImConfigCrypto(HexFormat.of().formatHex(kg.generateKey().getEncoded()));
        service = new ImBotServiceImpl(imBotMapper, senderSessionMapper, agentMapper,
                chatService, crypto, dingTalkSender, new ObjectMapper());
        RuntimeContext.set(Context.of("user-1", "ws-1"));
    }

    @AfterEach
    void tearDown() {
        RuntimeContext.clear();
    }

    private ImBotCreateDTO createDto(String platform, String mode, Map<String, String> config) {
        ImBotCreateDTO dto = new ImBotCreateDTO();
        dto.setName("测试机器人");
        dto.setPlatform(platform);
        dto.setMode(mode);
        dto.setConfig(config);
        return dto;
    }

    private AgentDO publishedAgent(String workspaceId) {
        AgentDO agent = new AgentDO();
        agent.setId("agent-1");
        agent.setWorkspaceId(workspaceId);
        agent.setStatus(AgentStatus.PUBLISHED);
        return agent;
    }

    private ImBotDO bot(String platform, String mode, String status) {
        ImBotDO bot = new ImBotDO();
        bot.setId("bot-1");
        bot.setWorkspaceId("ws-1");
        bot.setName("测试机器人");
        bot.setPlatform(platform);
        bot.setMode(mode);
        bot.setStatus(status);
        return bot;
    }

    // ==================== create 校验 ====================

    @Test
    void create_dingtalkWebhook_nonOfficialUrl_throws5202() {
        // SSRF 防护：服务端会向 webhookUrl 发请求，必须锁死官方域名
        ImBotCreateDTO dto = createDto("dingtalk", "webhook",
                Map.of("webhookUrl", "http://169.254.169.254/latest/meta-data"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(5202, ex.getCode());
        verify(imBotMapper, never()).insert(any(ImBotDO.class));
    }

    @Test
    void create_wecomWebhookMode_throws5202() {
        ImBotCreateDTO dto = createDto("wecom", "webhook", Map.of("webhookUrl", DINGTALK_URL));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(5202, ex.getCode());
    }

    @Test
    void create_wecomCallback_missingField_throws5202() {
        ImBotCreateDTO dto = createDto("wecom", "callback", Map.of("corpId", "corp-a"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(5202, ex.getCode());
        assertTrue(ex.getMessage().contains("agentId"));
    }

    @Test
    void create_wecomCallback_invalidEncodingAesKey_throws5204() {
        ImBotCreateDTO dto = createDto("wecom", "callback", Map.of(
                "corpId", "corp-a", "agentId", "1000002", "secret", "SECsecret",
                "token", "tok", "encodingAesKey", "not-base64!!!"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(5204, ex.getCode());
    }

    @Test
    void create_unknownPlatform_throws5202() {
        ImBotCreateDTO dto = createDto("feishu", "webhook", Map.of("webhookUrl", DINGTALK_URL));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(5202, ex.getCode());
    }

    @Test
    void create_agentInOtherWorkspace_throws5202() {
        when(agentMapper.selectById("agent-x")).thenReturn(publishedAgent("ws-other"));
        ImBotCreateDTO dto = createDto("dingtalk", "webhook", Map.of("webhookUrl", DINGTALK_URL));
        dto.setAgentId("agent-x");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(5202, ex.getCode());
    }

    @Test
    void create_dingtalkWebhook_success_encryptsConfigAndMasks() {
        ImBotCreateDTO dto = createDto("dingtalk", "webhook",
                Map.of("webhookUrl", DINGTALK_URL, "secret", "SEC1abcdef"));
        ImBotVO vo = service.create(dto);

        ArgumentCaptor<ImBotDO> captor = ArgumentCaptor.forClass(ImBotDO.class);
        verify(imBotMapper).insert(captor.capture());
        ImBotDO saved = captor.getValue();

        // 落库必须是密文，且能往返解密
        assertFalse(saved.getConfigEncrypted().contains(DINGTALK_URL));
        assertEquals("active", saved.getStatus());
        assertEquals("ws-1", saved.getWorkspaceId());
        // VO 只回掩码
        assertEquals("https://oapi.dingtalk.com/robot/send?access_token=***", vo.getConfigMasked().get("webhookUrl"));
        assertEquals("SEC1****", vo.getConfigMasked().get("secret"));
        assertFalse(vo.getConfigMasked().containsValue(DINGTALK_URL));
    }

    // ==================== send ====================

    private ImSendDTO sendDto() {
        ImSendDTO dto = new ImSendDTO();
        dto.setText("hello");
        dto.setMsgType("text");
        return dto;
    }

    @Test
    void send_disabledBot_throws5202() {
        when(imBotMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bot("dingtalk", "webhook", "disabled"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.send("bot-1", sendDto()));
        assertEquals(5202, ex.getCode());
        verify(dingTalkSender, never()).send(anyString(), any(), any(), any(), any());
    }

    @Test
    void send_wecomCallbackBot_throws5206() {
        ImBotDO wecom = bot("wecom", "callback", "active");
        wecom.setConfigEncrypted(crypto.encrypt("{\"corpId\":\"corp-a\"}"));
        when(imBotMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(wecom);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.send("bot-1", sendDto()));
        assertEquals(5206, ex.getCode());
    }

    @Test
    void send_dingtalkWebhook_delegatesToSenderWithDecryptedConfig() {
        ImBotDO ding = bot("dingtalk", "webhook", "active");
        ding.setConfigEncrypted(crypto.encrypt(
                "{\"webhookUrl\":\"" + DINGTALK_URL + "\",\"secret\":\"SEC1abcdef\"}"));
        when(imBotMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ding);

        ImSendDTO dto = sendDto();
        dto.setMsgType("markdown");
        dto.setTitle("标题");
        service.send("bot-1", dto);

        verify(dingTalkSender).send(DINGTALK_URL, "SEC1abcdef", "markdown", "标题", "hello");
    }

    // ==================== handleIncoming ====================

    private ImBotDO callbackBotWithAgent() {
        ImBotDO b = bot("wecom", "callback", "active");
        b.setAgentId("agent-1");
        return b;
    }

    @Test
    void handleIncoming_botMissing_returnsEmpty() {
        when(imBotMapper.selectById("bot-x")).thenReturn(null);
        assertEquals("", service.handleIncoming("bot-x", new ImIncoming("s1", null, "hi", "2")));
    }

    @Test
    void handleIncoming_botDisabled_returnsEmpty() {
        when(imBotMapper.selectById("bot-1")).thenReturn(bot("wecom", "callback", "disabled"));
        assertEquals("", service.handleIncoming("bot-1", new ImIncoming("s1", null, "hi", "2")));
    }

    @Test
    void handleIncoming_agentNotBound_returnsHint() {
        when(imBotMapper.selectById("bot-1")).thenReturn(bot("wecom", "callback", "active"));
        String reply = service.handleIncoming("bot-1", new ImIncoming("s1", null, "hi", "2"));
        assertTrue(reply.contains("未绑定 Agent"));
    }

    @Test
    void handleIncoming_agentInOtherWorkspace_returnsHint() {
        when(imBotMapper.selectById("bot-1")).thenReturn(callbackBotWithAgent());
        when(agentMapper.selectById("agent-1")).thenReturn(publishedAgent("ws-other"));
        String reply = service.handleIncoming("bot-1", new ImIncoming("s1", null, "hi", "2"));
        assertTrue(reply.contains("不存在"));
    }

    @Test
    void handleIncoming_agentUnpublished_returnsHint() {
        AgentDO draft = publishedAgent("ws-1");
        draft.setStatus(AgentStatus.DRAFT);
        when(imBotMapper.selectById("bot-1")).thenReturn(callbackBotWithAgent());
        when(agentMapper.selectById("agent-1")).thenReturn(draft);
        String reply = service.handleIncoming("bot-1", new ImIncoming("s1", null, "hi", "2"));
        assertTrue(reply.contains("尚未发布"));
    }

    @Test
    void handleIncoming_newSender_savesMappingAndInjectsImContext() {
        when(imBotMapper.selectById("bot-1")).thenReturn(callbackBotWithAgent());
        when(agentMapper.selectById("agent-1")).thenReturn(publishedAgent("ws-1"));
        when(senderSessionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        ChatResponseVO resp = new ChatResponseVO();
        resp.setSessionId("sess-1");
        resp.setReply("你好");
        when(chatService.chat(any(ChatRequestDTO.class))).thenAnswer(inv -> {
            // 对话执行时必须已注入虚拟用户上下文（IM 发送者 + 机器人所属租户）
            assertEquals("im:wecom:s1", RuntimeContext.getUserId());
            assertEquals("ws-1", RuntimeContext.getWorkspaceId());
            return resp;
        });

        String reply = service.handleIncoming("bot-1", new ImIncoming("s1", "张三", "你好", "2"));
        assertEquals("你好", reply);

        ArgumentCaptor<ChatRequestDTO> reqCaptor = ArgumentCaptor.forClass(ChatRequestDTO.class);
        verify(chatService).chat(reqCaptor.capture());
        assertEquals("agent-1", reqCaptor.getValue().getAgentId());
        assertNull(reqCaptor.getValue().getSessionId());

        ArgumentCaptor<ImSenderSessionDO> mapCaptor = ArgumentCaptor.forClass(ImSenderSessionDO.class);
        verify(senderSessionMapper).insert(mapCaptor.capture());
        assertEquals("bot-1", mapCaptor.getValue().getBotId());
        assertEquals("s1", mapCaptor.getValue().getSenderId());
        assertEquals("sess-1", mapCaptor.getValue().getSessionId());
    }

    @Test
    void handleIncoming_existingSender_reusesSessionWithoutInsert() {
        when(imBotMapper.selectById("bot-1")).thenReturn(callbackBotWithAgent());
        when(agentMapper.selectById("agent-1")).thenReturn(publishedAgent("ws-1"));
        ImSenderSessionDO mapping = new ImSenderSessionDO();
        mapping.setBotId("bot-1");
        mapping.setSenderId("s1");
        mapping.setSessionId("sess-9");
        when(senderSessionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mapping);
        ChatResponseVO resp = new ChatResponseVO();
        resp.setSessionId("sess-9");
        resp.setReply("又见面了");
        when(chatService.chat(any(ChatRequestDTO.class))).thenReturn(resp);

        service.handleIncoming("bot-1", new ImIncoming("s1", null, "继续聊", "2"));

        ArgumentCaptor<ChatRequestDTO> reqCaptor = ArgumentCaptor.forClass(ChatRequestDTO.class);
        verify(chatService).chat(reqCaptor.capture());
        assertEquals("sess-9", reqCaptor.getValue().getSessionId());
        verify(senderSessionMapper, never()).insert(any(ImSenderSessionDO.class));
    }

    @Test
    void handleIncoming_chatBusinessException_returnsFriendlyMessage() {
        when(imBotMapper.selectById("bot-1")).thenReturn(callbackBotWithAgent());
        when(agentMapper.selectById("agent-1")).thenReturn(publishedAgent("ws-1"));
        when(senderSessionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(chatService.chat(any(ChatRequestDTO.class)))
                .thenThrow(new BusinessException(6018, "Agent 未配置模型"));

        String reply = service.handleIncoming("bot-1", new ImIncoming("s1", null, "hi", "2"));
        assertEquals("处理失败：Agent 未配置模型", reply);
    }

    @Test
    void handleIncoming_longReply_truncated() {
        when(imBotMapper.selectById("bot-1")).thenReturn(callbackBotWithAgent());
        when(agentMapper.selectById("agent-1")).thenReturn(publishedAgent("ws-1"));
        when(senderSessionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        ChatResponseVO resp = new ChatResponseVO();
        resp.setSessionId("sess-1");
        resp.setReply("a".repeat(3000));
        when(chatService.chat(any(ChatRequestDTO.class))).thenReturn(resp);

        String reply = service.handleIncoming("bot-1", new ImIncoming("s1", null, "hi", "2"));
        assertTrue(reply.startsWith("a".repeat(2000)));
        assertTrue(reply.contains("内容过长已截断"));
    }

    @Test
    void handleIncoming_clearsRuntimeContextAfterwards() {
        when(imBotMapper.selectById("bot-1")).thenReturn(callbackBotWithAgent());
        when(agentMapper.selectById("agent-1")).thenReturn(publishedAgent("ws-1"));
        when(senderSessionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(chatService.chat(any(ChatRequestDTO.class))).thenThrow(new RuntimeException("boom"));

        service.handleIncoming("bot-1", new ImIncoming("s1", null, "hi", "2"));
        // 回调线程是 Web 线程，虚拟用户上下文必须清理，防止串到下一个请求
        assertNull(RuntimeContext.get());
    }
}
