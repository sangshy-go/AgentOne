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
import com.agentone.im.adapter.WecomCrypto;
import com.agentone.im.crypto.ImConfigCrypto;
import com.agentone.im.dto.ImBotCreateDTO;
import com.agentone.im.dto.ImBotUpdateDTO;
import com.agentone.im.dto.ImSendDTO;
import com.agentone.im.entity.ImBotDO;
import com.agentone.im.entity.ImSenderSessionDO;
import com.agentone.im.mapper.ImBotMapper;
import com.agentone.im.mapper.ImSenderSessionMapper;
import com.agentone.im.service.ImBotService;
import com.agentone.im.vo.ImBotVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IM Bot 网关实现（课题⑤）。
 *
 * 安全要点：
 * 1. 凭证全程密文：create/update 加密、list 只回掩码、发送/回调时即时解密
 * 2. 钉钉 webhook URL 强制官方域名（服务端发起请求，防 SSRF）
 * 3. 回调注入的运行时上下文 userId = im:{platform}:{senderId}，workspace 取机器人所属空间
 *
 * 已知限制（文档同步）：
 * - IM 回复走同步对话，长耗时模型可能超过企微被动回复 5 秒窗口
 * - 企微主动发送需要应用 access_token 协议，本版未实现（5206）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImBotServiceImpl implements ImBotService {

    /** IM 回复长度上限（平台被动回复字节数限制，超长截断） */
    private static final int REPLY_MAX_LEN = 2000;

    private final ImBotMapper imBotMapper;
    private final ImSenderSessionMapper senderSessionMapper;
    private final AgentMapper agentMapper;
    private final ChatService chatService;
    private final ImConfigCrypto crypto;
    private final DingTalkSender dingTalkSender;
    private final ObjectMapper objectMapper;

    @Override
    public List<ImBotVO> list() {
        String workspaceId = RuntimeContext.getWorkspaceId();
        List<ImBotDO> bots = imBotMapper.selectList(
                new LambdaQueryWrapper<ImBotDO>()
                        .eq(ImBotDO::getWorkspaceId, workspaceId)
                        .orderByDesc(ImBotDO::getCreatedAt));
        return bots.stream().map(this::toVO).toList();
    }

    @Override
    public ImBotVO create(ImBotCreateDTO dto) {
        String workspaceId = RuntimeContext.getWorkspaceId();
        Map<String, String> config = validateAndNormalize(dto.getPlatform(), dto.getMode(),
                dto.getConfig(), dto.getAgentId());

        ImBotDO bot = new ImBotDO();
        bot.setWorkspaceId(workspaceId);
        bot.setName(dto.getName());
        bot.setPlatform(dto.getPlatform());
        bot.setMode(dto.getMode());
        bot.setAgentId(emptyToNull(dto.getAgentId()));
        bot.setConfigEncrypted(encryptConfig(config));
        // 创建即停用（测试期）：先在控制台发送测试验证凭证，确认无误后手动启用才接收真实流量
        bot.setStatus("disabled");
        bot.setCreatedBy(RuntimeContext.getUserId());
        bot.setCreatedAt(LocalDateTime.now());
        bot.setUpdatedAt(LocalDateTime.now());
        imBotMapper.insert(bot);
        log.info("IM 机器人创建: id={}, platform={}, mode={}, workspace={}",
                bot.getId(), bot.getPlatform(), bot.getMode(), workspaceId);
        return toVO(bot);
    }

    @Override
    public ImBotVO update(String id, ImBotUpdateDTO dto) {
        ImBotDO bot = loadOwnedBot(id);
        if (dto.getName() != null && !dto.getName().isBlank()) {
            bot.setName(dto.getName());
        }
        if (dto.getStatus() != null) {
            if (!"active".equals(dto.getStatus()) && !"disabled".equals(dto.getStatus())) {
                throw new BusinessException(5202, "status 仅支持 active / disabled");
            }
            bot.setStatus(dto.getStatus());
        }
        if (dto.getAgentId() != null) {
            bot.setAgentId(emptyToNull(dto.getAgentId()));
        }
        if (dto.getConfig() != null && !dto.getConfig().isEmpty()) {
            Map<String, String> config = validateAndNormalize(bot.getPlatform(), bot.getMode(),
                    dto.getConfig(), bot.getAgentId());
            bot.setConfigEncrypted(encryptConfig(config));
        }
        bot.setUpdatedAt(LocalDateTime.now());
        imBotMapper.updateById(bot);
        return toVO(bot);
    }

    @Override
    public void delete(String id) {
        ImBotDO bot = loadOwnedBot(id);
        imBotMapper.deleteById(bot.getId());
        // im_sender_session 由 ON DELETE CASCADE 清理
        log.info("IM 机器人删除: id={}", id);
    }

    @Override
    public void send(String id, ImSendDTO dto) {
        ImBotDO bot = loadOwnedBot(id);
        // 不做启停门禁：本接口是控制台手动测试通道（测试发送恰在启用前进行）；
        // 启停状态只管控回调入口的真实流量（见 handleIncoming）
        Map<String, String> config = decryptConfig(bot);
        if ("dingtalk".equals(bot.getPlatform()) && "webhook".equals(bot.getMode())) {
            dingTalkSender.send(config.get("webhookUrl"), config.get("secret"),
                    dto.getMsgType(), dto.getTitle(), dto.getText());
            return;
        }
        // 企微自建应用发送需 access_token 协议（获取/刷新/发送三接口），本版不实现；
        // 钉钉企业应用（callback 模式）的主动发送同样需要应用凭证接口。明确报错而非静默。
        throw new BusinessException(5206, "当前仅支持钉钉自定义机器人（webhook）发送；企微/企业应用主动发送需应用凭证接口，待后续版本支持");
    }

    @Override
    public String handleIncoming(String botId, ImIncoming incoming) {
        // selectById 走 @InterceptorIgnore：回调无用户上下文，botId 即访问凭证
        ImBotDO bot = imBotMapper.selectById(botId);
        if (bot == null || !"active".equals(bot.getStatus()) || !"callback".equals(bot.getMode())) {
            return "";
        }

        // 注入运行时上下文：IM 发送者作为虚拟用户，租户取机器人所属工作空间。
        // 必须先于 agentMapper 等后续查询（租户拦截器依赖该上下文做数据隔离）
        Context ctx = Context.of("im:" + bot.getPlatform() + ":" + incoming.senderId(), bot.getWorkspaceId());
        RuntimeContext.set(ctx);
        try {
            if (bot.getAgentId() == null) {
                return "该机器人未绑定 Agent，请先在 AgentOne 控制台完成绑定";
            }
            AgentDO agent = agentMapper.selectById(bot.getAgentId());
            // 租户拦截器已按工作空间过滤，显式等值校验为纵深防御
            if (agent == null || !agent.getWorkspaceId().equals(bot.getWorkspaceId())) {
                return "绑定的 Agent 不存在，请联系管理员检查机器人配置";
            }
            if (agent.getStatus() != AgentStatus.PUBLISHED) {
                return "绑定的 Agent 尚未发布，暂不能对外对话";
            }

            ImSenderSessionDO mapping = senderSessionMapper.selectOne(
                    new LambdaQueryWrapper<ImSenderSessionDO>()
                            .eq(ImSenderSessionDO::getBotId, botId)
                            .eq(ImSenderSessionDO::getSenderId, incoming.senderId()));

            ChatRequestDTO chatRequest = new ChatRequestDTO();
            chatRequest.setAgentId(bot.getAgentId());
            chatRequest.setSessionId(mapping != null ? mapping.getSessionId() : null);
            chatRequest.setMessage(incoming.text());
            ChatResponseVO response = chatService.chat(chatRequest);

            if (mapping == null && response.getSessionId() != null) {
                ImSenderSessionDO newMapping = new ImSenderSessionDO();
                newMapping.setBotId(botId);
                newMapping.setSenderId(incoming.senderId());
                newMapping.setSessionId(response.getSessionId());
                senderSessionMapper.insert(newMapping);
            }
            return truncate(response.getReply());
        } catch (BusinessException e) {
            // 业务异常文案面向用户可展示（如 6018 未配置模型），直接透出便于排障
            log.warn("IM 对话业务异常: botId={}, code={}, msg={}", botId, e.getCode(), e.getMessage());
            return truncate("处理失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("IM 对话异常: botId={}", botId, e);
            return "处理失败，请稍后重试或联系管理员";
        } finally {
            RuntimeContext.clear();
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 平台/模式/配置组合校验，返回规范化后的 config
     */
    private Map<String, String> validateAndNormalize(String platform, String mode,
                                                     Map<String, String> config, String agentId) {
        if (agentId != null && !agentId.isBlank()) {
            checkAgent(agentId);
        }
        Map<String, String> normalized = new HashMap<>();
        if ("dingtalk".equals(platform) && "webhook".equals(mode)) {
            String url = requireField(config, "webhookUrl");
            // 服务端将向该 URL 发起请求：强制钉钉官方域名，防 SSRF
            if (!url.startsWith("https://oapi.dingtalk.com/")) {
                throw new BusinessException(5202, "webhookUrl 必须是 https://oapi.dingtalk.com/ 官方地址");
            }
            normalized.put("webhookUrl", url);
            String secret = config.get("secret");
            if (secret != null && !secret.isBlank()) {
                normalized.put("secret", secret);
            }
            return normalized;
        }
        if ("dingtalk".equals(platform) && "callback".equals(mode)) {
            normalized.put("appSecret", requireField(config, "appSecret"));
            return normalized;
        }
        if ("wecom".equals(platform) && "callback".equals(mode)) {
            normalized.put("corpId", requireField(config, "corpId"));
            normalized.put("agentId", requireField(config, "agentId"));
            normalized.put("secret", requireField(config, "secret"));
            normalized.put("token", requireField(config, "token"));
            normalized.put("encodingAesKey", requireField(config, "encodingAesKey"));
            // 创建时即校验 encodingAesKey 合法性（构造即校验），避免回调时才发现配置错误
            new WecomCrypto(normalized.get("token"), normalized.get("encodingAesKey"), normalized.get("corpId"));
            return normalized;
        }
        if ("wecom".equals(platform) && "webhook".equals(mode)) {
            throw new BusinessException(5202, "企微没有 webhook 机器人形态，请选择 callback 模式（自建应用）");
        }
        throw new BusinessException(5202, "不支持的 platform/mode 组合：" + platform + "/" + mode);
    }

    private void checkAgent(String agentId) {
        String workspaceId = RuntimeContext.getWorkspaceId();
        AgentDO agent = agentMapper.selectById(agentId);
        if (agent == null || !workspaceId.equals(agent.getWorkspaceId())) {
            throw new BusinessException(5202, "绑定的 Agent 不存在或不属于当前工作空间");
        }
    }

    private String requireField(Map<String, String> config, String key) {
        String value = config.get(key);
        if (value == null || value.isBlank()) {
            throw new BusinessException(5202, "config 缺少必填项：" + key);
        }
        return value.trim();
    }

    private ImBotDO loadOwnedBot(String id) {
        String workspaceId = RuntimeContext.getWorkspaceId();
        ImBotDO bot = imBotMapper.selectOne(
                new LambdaQueryWrapper<ImBotDO>()
                        .eq(ImBotDO::getId, id)
                        .eq(ImBotDO::getWorkspaceId, workspaceId));
        if (bot == null) {
            throw new BusinessException(5202, "IM 机器人不存在");
        }
        return bot;
    }

    private String encryptConfig(Map<String, String> config) {
        try {
            return crypto.encrypt(objectMapper.writeValueAsString(config));
        } catch (Exception e) {
            throw new IllegalStateException("IM 配置序列化失败", e);
        }
    }

    private Map<String, String> decryptConfig(ImBotDO bot) {
        try {
            return objectMapper.readValue(crypto.decrypt(bot.getConfigEncrypted()),
                    new TypeReference<Map<String, String>>() {
                    });
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("IM 配置反序列化失败", e);
        }
    }

    private ImBotVO toVO(ImBotDO bot) {
        ImBotVO vo = new ImBotVO();
        vo.setId(bot.getId());
        vo.setName(bot.getName());
        vo.setPlatform(bot.getPlatform());
        vo.setMode(bot.getMode());
        vo.setAgentId(bot.getAgentId());
        vo.setStatus(bot.getStatus());
        vo.setCreatedBy(bot.getCreatedBy());
        vo.setCreatedAt(bot.getCreatedAt());
        vo.setConfigMasked(maskConfig(bot));
        return vo;
    }

    /**
     * 掩码摘要：绝不回传原始凭证。解密失败（如密钥更换）时给提示而非报错，列表仍可用
     */
    private Map<String, String> maskConfig(ImBotDO bot) {
        Map<String, String> masked = new HashMap<>();
        Map<String, String> config;
        try {
            config = decryptConfig(bot);
        } catch (Exception e) {
            masked.put("_error", "配置无法解密（密钥可能已更换）");
            return masked;
        }
        config.forEach((k, v) -> {
            if ("webhookUrl".equals(k)) {
                int idx = v.indexOf("access_token=");
                masked.put(k, idx > 0 ? v.substring(0, idx) + "access_token=***" : v.replaceAll("\\?.*", "?***"));
            } else if (v.length() > 6) {
                masked.put(k, v.substring(0, 4) + "****");
            } else {
                masked.put(k, "****");
            }
        });
        return masked;
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= REPLY_MAX_LEN ? text : text.substring(0, REPLY_MAX_LEN) + "\n…（内容过长已截断）";
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
