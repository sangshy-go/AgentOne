package com.agentone.im.adapter;

import com.agentone.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉自定义机器人 webhook 发送（加签安全设置）。
 * 加签算法（官方文档）：sign = urlencode(base64(HmacSHA256(timestamp + "\n" + secret, secret)))
 * 该签名算法同时用于企业机器人回调验签（见 ImCallbackController）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DingTalkSender {

    private final ObjectMapper objectMapper;

    // 强制 HTTP/1.1：与 McpConnectionManager 同理，避免 JDK 默认 HTTP/2 的 ALPN 兼容问题
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 发送文本或 markdown 消息
     */
    public void send(String webhookUrl, String secret, String msgType, String title, String content) {
        String url = buildUrl(webhookUrl, secret, System.currentTimeMillis());
        Map<String, Object> body = new HashMap<>();
        if ("markdown".equals(msgType)) {
            body.put("msgtype", "markdown");
            body.put("markdown", Map.of("title", title == null || title.isBlank() ? "AgentOne" : title,
                    "text", content));
        } else {
            body.put("msgtype", "text");
            body.put("text", Map.of("content", content));
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            int errcode = json.path("errcode").asInt(-1);
            if (errcode != 0) {
                String errmsg = json.path("errmsg").asText("未知错误");
                log.warn("钉钉机器人发送失败: errcode={}, errmsg={}", errcode, errmsg);
                throw new BusinessException(5203, "钉钉发送失败（" + errcode + "）：" + errmsg);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("钉钉机器人发送异常", e);
            throw new BusinessException(5203, "钉钉发送异常：" + e.getMessage());
        }
    }

    /**
     * webhook 追加加签参数（未配置 secret 时原样返回）
     */
    public static String buildUrl(String webhookUrl, String secret, long timestamp) {
        if (secret == null || secret.isBlank()) {
            return webhookUrl;
        }
        String sign = sign(timestamp, secret);
        String sep = webhookUrl.contains("?") ? "&" : "?";
        return webhookUrl + sep + "timestamp=" + timestamp + "&sign=" + sign;
    }

    /**
     * 钉钉加签/验签通用算法
     */
    public static String sign(long timestamp, String secret) {
        try {
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("钉钉签名计算失败", e);
        }
    }
}
