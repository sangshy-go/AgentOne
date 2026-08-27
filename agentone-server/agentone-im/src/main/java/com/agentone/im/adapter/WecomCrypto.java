package com.agentone.im.adapter;

import com.agentone.common.exception.BusinessException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 企微自建应用回调消息加解密（按官方 WXBizMsgCrypt 协议实现）。
 * - 签名：sha1(sort(token, timestamp, nonce, encrypt) 拼接)
 * - 加密：AES-256-CBC，key = base64decode(encodingAesKey + "=")，iv = key 前 16 字节，
 *   明文布局：random(16) + msgLen(4, 大端) + msg + receiveId，PKCS#7 按 32 字节块补齐
 * 非单例：每个企微机器人配置独立实例（token / receiveId 不同）
 */
public class WecomCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BLOCK_SIZE = 32;

    private final String token;
    private final String receiveId;
    private final byte[] aesKey;

    public WecomCrypto(String token, String encodingAesKey, String receiveId) {
        this.token = token;
        this.receiveId = receiveId;
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encodingAesKey + "=");
        } catch (IllegalArgumentException e) {
            throw new BusinessException(5204, "企微 encodingAesKey 非法（应为 43 位 Base64 字符串）");
        }
        if (key.length != 32) {
            throw new BusinessException(5204, "企微 encodingAesKey 非法（解码后应为 32 字节）");
        }
        this.aesKey = key;
    }

    /**
     * 校验签名；不匹配抛 5205
     */
    public void verifySignature(String signature, String timestamp, String nonce, String encrypt) {
        String expected = sha1Sign(token, timestamp, nonce, encrypt);
        if (!expected.equals(signature)) {
            throw new BusinessException(5205, "企微回调签名校验失败");
        }
    }

    /**
     * 解密 Encrypt 字段，返回明文消息（校验 receiveId 尾部）
     */
    public String decrypt(String encryptB64) {
        try {
            byte[] cipherBytes = Base64.getDecoder().decode(encryptB64);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                    new IvParameterSpec(Arrays.copyOf(aesKey, 16)));
            byte[] padded = cipher.doFinal(cipherBytes);
            byte[] data = stripPkcs7(padded);
            // random(16) + msgLen(4) + msg + receiveId
            int msgLen = ByteBuffer.wrap(data, 16, 4).order(ByteOrder.BIG_ENDIAN).getInt();
            String msg = new String(data, 20, msgLen, StandardCharsets.UTF_8);
            String tailReceiveId = new String(data, 20 + msgLen, data.length - 20 - msgLen, StandardCharsets.UTF_8);
            if (!receiveId.equals(tailReceiveId)) {
                throw new BusinessException(5205, "企微回调 receiveId 不匹配");
            }
            return msg;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(5205, "企微回调解密失败：" + e.getMessage());
        }
    }

    /**
     * 加密回复明文，返回 Base64 密文
     */
    public String encrypt(String plaintext) {
        try {
            byte[] random = new byte[16];
            RANDOM.nextBytes(random);
            byte[] msgBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] receiveIdBytes = receiveId.getBytes(StandardCharsets.UTF_8);
            byte[] lenBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
                    .putInt(msgBytes.length).array();

            int total = random.length + lenBytes.length + msgBytes.length + receiveIdBytes.length;
            int padLen = BLOCK_SIZE - (total % BLOCK_SIZE);
            byte[] padded = new byte[total + padLen];
            int pos = 0;
            System.arraycopy(random, 0, padded, pos, random.length);
            pos += random.length;
            System.arraycopy(lenBytes, 0, padded, pos, lenBytes.length);
            pos += lenBytes.length;
            System.arraycopy(msgBytes, 0, padded, pos, msgBytes.length);
            pos += msgBytes.length;
            System.arraycopy(receiveIdBytes, 0, padded, pos, receiveIdBytes.length);
            Arrays.fill(padded, total, padded.length, (byte) padLen);

            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                    new IvParameterSpec(Arrays.copyOf(aesKey, 16)));
            return Base64.getEncoder().encodeToString(cipher.doFinal(padded));
        } catch (Exception e) {
            throw new IllegalStateException("企微回复加密失败", e);
        }
    }

    /**
     * 生成被动回复 XML 信封（Encrypt + 签名）
     */
    public String buildReplyEnvelope(String encrypted, String timestamp, String nonce) {
        String signature = sha1Sign(token, timestamp, nonce, encrypted);
        return "<xml>\n"
                + "<Encrypt><![CDATA[" + encrypted + "]]></Encrypt>\n"
                + "<MsgSignature><![CDATA[" + signature + "]]></MsgSignature>\n"
                + "<TimeStamp>" + timestamp + "</TimeStamp>\n"
                + "<Nonce><![CDATA[" + nonce + "]]></Nonce>\n"
                + "</xml>";
    }

    /**
     * 企微签名：参与项字典序排序后拼接，SHA1 hex 小写
     */
    public static String sha1Sign(String... parts) {
        try {
            String[] sorted = parts.clone();
            Arrays.sort(sorted);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(String.join("", sorted).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("企微签名计算失败", e);
        }
    }

    private static byte[] stripPkcs7(byte[] data) {
        int pad = data[data.length - 1] & 0xFF;
        if (pad < 1 || pad > BLOCK_SIZE) {
            return data;
        }
        return Arrays.copyOf(data, data.length - pad);
    }
}
