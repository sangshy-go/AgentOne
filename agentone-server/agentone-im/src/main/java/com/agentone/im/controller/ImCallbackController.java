package com.agentone.im.controller;

import com.agentone.im.adapter.DingTalkSender;
import com.agentone.im.adapter.ImIncoming;
import com.agentone.im.adapter.WecomCrypto;
import com.agentone.im.crypto.ImConfigCrypto;
import com.agentone.im.entity.ImBotDO;
import com.agentone.im.mapper.ImBotMapper;
import com.agentone.im.service.ImBotService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IM 平台回调入口（公开端点，不走 JWT：由平台签名机制鉴权）。
 * - 企微自建应用：GET URL 验证（明文回显 echostr）+ POST 加密消息（被动回复密文）
 * - 钉钉企业机器人回调：POST JSON + timestamp/sign 头验签
 * 注意：botId 在 URL 中，机器人停用/不存在时静默返回，避免平台重试风暴
 */
@Slf4j
@RestController
@RequestMapping("/api/im/callback")
@RequiredArgsConstructor
public class ImCallbackController {

    private static final Pattern ENCRYPT_PATTERN =
            Pattern.compile("<Encrypt><!\\[CDATA\\[(.*?)\\]\\]></Encrypt>", Pattern.DOTALL);

    private final ImBotService imBotService;
    private final ImBotMapper imBotMapper;
    private final ImConfigCrypto configCrypto;
    private final ObjectMapper objectMapper;

    // ==================== 企微 ====================

    /**
     * 企微回调 URL 验证：校验签名后解密 echostr 并明文回显
     */
    @GetMapping(value = "/wecom/{botId}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String wecomVerify(@PathVariable String botId,
                              @RequestParam("msg_signature") String msgSignature,
                              @RequestParam String timestamp,
                              @RequestParam String nonce,
                              @RequestParam String echostr) {
        WecomCrypto crypto = wecomCryptoOf(botId);
        if (crypto == null) {
            return "";
        }
        crypto.verifySignature(msgSignature, timestamp, nonce, echostr);
        return crypto.decrypt(echostr);
    }

    /**
     * 企微消息回调：解密 → 统一入口 → 被动回复（加密 XML）
     */
    @PostMapping(value = "/wecom/{botId}", produces = MediaType.APPLICATION_XML_VALUE)
    public String wecomMessage(@PathVariable String botId,
                               @RequestParam("msg_signature") String msgSignature,
                               @RequestParam String timestamp,
                               @RequestParam String nonce,
                               @RequestBody(required = false) String body) {
        WecomCrypto crypto = wecomCryptoOf(botId);
        if (crypto == null) {
            return "success";
        }
        if (body == null || body.isEmpty()) {
            return "success";
        }
        String encrypt = extractEncrypt(body);
        if (encrypt == null) {
            return "success";
        }
        crypto.verifySignature(msgSignature, timestamp, nonce, encrypt);
        String xml = crypto.decrypt(encrypt);

        // 仅处理文本消息；其他类型（图片/事件）静默
        String msgType = xmlField(xml, "MsgType");
        if (!"text".equals(msgType)) {
            return "success";
        }
        ImIncoming incoming = new ImIncoming(
                xmlField(xml, "FromUserName"), null, xmlField(xml, "Content"), "1");
        String reply = imBotService.handleIncoming(botId, incoming);
        if (reply == null || reply.isEmpty()) {
            return "success";
        }
        String replyXml = "<xml>"
                + "<ToUserName><![CDATA[" + incoming.senderId() + "]]></ToUserName>"
                + "<FromUserName><![CDATA[" + xmlField(xml, "ToUserName") + "]]></FromUserName>"
                + "<CreateTime>" + System.currentTimeMillis() / 1000 + "</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[" + reply + "]]></Content>"
                + "</xml>";
        return crypto.buildReplyEnvelope(crypto.encrypt(replyXml), timestamp, nonce);
    }

    // ==================== 钉钉 ====================

    /**
     * 钉钉企业机器人消息回调：timestamp + sign 头验签（签名算法同自定义机器人加签），
     * body 为平台推送的 JSON（msgtype/text/senderStaffId/conversationType）
     */
    @PostMapping("/dingtalk/{botId}")
    public Map<String, Object> dingtalkMessage(@PathVariable String botId,
                                               @RequestHeader(value = "timestamp", required = false) String timestamp,
                                               @RequestHeader(value = "sign", required = false) String sign,
                                               @RequestBody(required = false) String body) {
        ImBotDO bot = imBotMapper.selectById(botId);
        if (bot == null || !"active".equals(bot.getStatus()) || !"callback".equals(bot.getMode())) {
            return Map.of();
        }
        if (body == null || body.isEmpty()) {
            return Map.of();
        }
        Map<String, String> config;
        try {
            config = decryptForCallback(bot);
        } catch (Exception e) {
            log.warn("钉钉回调配置解密失败: botId={}", botId);
            return Map.of();
        }
        String appSecret = config.get("appSecret");
        if (appSecret != null && !appSecret.isBlank()) {
            if (timestamp == null || sign == null || !verifyDingtalkSign(timestamp, sign, appSecret)) {
                log.warn("钉钉回调验签失败: botId={}", botId);
                return Map.of();
            }
        }
        try {
            JsonNode json = objectMapper.readTree(body);
            String msgType = json.path("msgtype").asText("");
            if (!"text".equals(msgType)) {
                return Map.of();
            }
            ImIncoming incoming = new ImIncoming(
                    json.path("senderStaffId").asText(""),
                    json.path("senderNick").asText(null),
                    json.path("text").path("content").asText("").trim(),
                    json.path("conversationType").asText("2"));
            String reply = imBotService.handleIncoming(botId, incoming);
            if (reply == null || reply.isEmpty()) {
                return Map.of();
            }
            // 通过 HTTP 响应直接回复（钉钉支持的机器人回调响应形态）
            return Map.of("msgtype", "text", "text", Map.of("content", reply));
        } catch (Exception e) {
            log.error("钉钉回调处理异常: botId={}", botId, e);
            return Map.of();
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 构造企微加解密器；机器人不存在/停用/非 callback/配置不可解时返回 null（静默）
     */
    private WecomCrypto wecomCryptoOf(String botId) {
        ImBotDO bot = imBotMapper.selectById(botId);
        if (bot == null || !"active".equals(bot.getStatus()) || !"callback".equals(bot.getMode())
                || !"wecom".equals(bot.getPlatform())) {
            return null;
        }
        try {
            Map<String, String> config = decryptForCallback(bot);
            return new WecomCrypto(config.get("token"), config.get("encodingAesKey"), config.get("corpId"));
        } catch (Exception e) {
            log.warn("企微回调配置不可用: botId={}", botId, e);
            return null;
        }
    }

    private Map<String, String> decryptForCallback(ImBotDO bot) {
        try {
            return objectMapper.readValue(configCrypto.decrypt(bot.getConfigEncrypted()),
                    new TypeReference<Map<String, String>>() {
                    });
        } catch (Exception e) {
            throw new IllegalStateException("IM 配置解密失败", e);
        }
    }

    private boolean verifyDingtalkSign(String timestamp, String sign, String appSecret) {
        try {
            long ts = Long.parseLong(timestamp);
            // 允许 1 小时时钟偏移，防重放窗口与钉钉官方一致
            if (Math.abs(System.currentTimeMillis() - ts) > 3600_000L) {
                return false;
            }
            String expected = DingTalkSender.sign(ts, appSecret);
            return expected.equals(sign) || expected.equals(URLDecoder.decode(sign, StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String extractEncrypt(String body) {
        Matcher matcher = ENCRYPT_PATTERN.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 提取解密后 XML 的字段值（CDATA 或纯文本）。
     * 解密+验签后的报文来自平台，用正则取字段，不引入完整 XML 解析器
     */
    private static String xmlField(String xml, String field) {
        Matcher cdata = Pattern.compile("<" + field + "><!\\[CDATA\\[(.*?)\\]\\]></" + field + ">", Pattern.DOTALL)
                .matcher(xml);
        if (cdata.find()) {
            return cdata.group(1);
        }
        Matcher plain = Pattern.compile("<" + field + ">(.*?)</" + field + ">", Pattern.DOTALL).matcher(xml);
        return plain.find() ? plain.group(1) : "";
    }
}
