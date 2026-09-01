package com.agentone.im.crypto;

import com.agentone.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * IM 凭证配置加解密（AES-256-GCM）。
 * 凭证是第三方密钥（webhook token / 企微 secret 等），明文落库等于泄露发送能力，
 * 故整体加密存储；密钥来自环境变量 AGENTONE_IM_SECRET_KEY（64 位 hex = 32 字节），
 * 未配置时创建/更新机器人直接失败（5200），不做明文降级。
 */
@Component
public class ImConfigCrypto {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    public ImConfigCrypto(@Value("${AGENTONE_IM_SECRET_KEY:}") String keyHex) {
        this.key = parseKey(keyHex);
    }

    private static SecretKey parseKey(String hex) {
        if (hex == null || hex.isBlank()) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = HexFormat.of().parseHex(hex.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("AGENTONE_IM_SECRET_KEY 必须是 hex 编码", e);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("AGENTONE_IM_SECRET_KEY 必须是 64 位 hex（32 字节），可用 openssl rand -hex 32 生成");
        }
        return new SecretKeySpec(bytes, "AES");
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(String plaintext) {
        requireKey();
        try {
            byte[] iv = new byte[IV_LEN];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LEN + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ciphertext, 0, out, IV_LEN, ciphertext.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("IM 配置加密失败", e);
        }
    }

    public String decrypt(String ciphertextB64) {
        requireKey();
        try {
            byte[] in = Base64.getDecoder().decode(ciphertextB64);
            if (in.length <= IV_LEN) {
                throw new BusinessException(5201, "IM 配置密文损坏");
            }
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(in, 0, iv, 0, IV_LEN);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(in, IV_LEN, in.length - IV_LEN);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new BusinessException(5201, "IM 配置解密失败：密文损坏或 AGENTONE_IM_SECRET_KEY 与加密时不一致");
        } catch (BusinessException e) {
            throw e;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BusinessException(5201, "IM 配置密文损坏，无法解析");
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new BusinessException(5200,
                    "未配置 IM 加密密钥：请设置环境变量 AGENTONE_IM_SECRET_KEY（openssl rand -hex 32 生成）后重启");
        }
    }
}
