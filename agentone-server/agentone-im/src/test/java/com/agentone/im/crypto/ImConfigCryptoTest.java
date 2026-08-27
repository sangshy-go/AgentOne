package com.agentone.im.crypto;

import com.agentone.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ImConfigCrypto 单测：加解密往返、未配置密钥 5200、密文篡改 5201、密钥长度校验
 */
class ImConfigCryptoTest {

    private static String randomKeyHex() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(256);
            byte[] bytes = kg.generateKey().getEncoded();
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void encryptDecrypt_roundtrip_returnsOriginal() {
        ImConfigCrypto crypto = new ImConfigCrypto(randomKeyHex());
        String plaintext = "{\"webhookUrl\":\"https://oapi.dingtalk.com/robot/send?access_token=abc\",\"secret\":\"SEC-中文\"}";
        String ciphertext = crypto.encrypt(plaintext);
        assertNotEquals(plaintext, ciphertext);
        assertEquals(plaintext, crypto.decrypt(ciphertext));
    }

    @Test
    void encrypt_samePlaintextTwice_producesDifferentCiphertext() {
        // 随机 IV：相同明文两次加密密文必须不同，否则泄露明文相同性信息
        ImConfigCrypto crypto = new ImConfigCrypto(randomKeyHex());
        assertNotEquals(crypto.encrypt("same"), crypto.encrypt("same"));
    }

    @Test
    void decrypt_tamperedCiphertext_throws5201() {
        ImConfigCrypto crypto = new ImConfigCrypto(randomKeyHex());
        byte[] raw = Base64.getDecoder().decode(crypto.encrypt("payload"));
        raw[raw.length - 1] ^= 0x01; // 翻转认证标签一位
        String tampered = Base64.getEncoder().encodeToString(raw);
        BusinessException ex = assertThrows(BusinessException.class, () -> crypto.decrypt(tampered));
        assertEquals(5201, ex.getCode());
    }

    @Test
    void decrypt_withDifferentKey_throws5201() {
        String ciphertext = new ImConfigCrypto(randomKeyHex()).encrypt("payload");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> new ImConfigCrypto(randomKeyHex()).decrypt(ciphertext));
        assertEquals(5201, ex.getCode());
    }

    @Test
    void encrypt_keyNotConfigured_throws5200() {
        ImConfigCrypto crypto = new ImConfigCrypto("");
        assertFalse(crypto.isConfigured());
        BusinessException ex = assertThrows(BusinessException.class, () -> crypto.encrypt("payload"));
        assertEquals(5200, ex.getCode());
    }

    @Test
    void decrypt_garbageInput_throws5201() {
        ImConfigCrypto crypto = new ImConfigCrypto(randomKeyHex());
        BusinessException ex = assertThrows(BusinessException.class, () -> crypto.decrypt("not-valid-base64!!!"));
        assertEquals(5201, ex.getCode());
    }

    @Test
    void constructor_wrongKeyLength_throws() {
        assertThrows(IllegalStateException.class, () -> new ImConfigCrypto("abcd"));
        assertThrows(IllegalStateException.class, () -> new ImConfigCrypto("zz".repeat(32)));
        assertTrue(new ImConfigCrypto(randomKeyHex()).isConfigured());
    }

    @Test
    void constructor_blankKey_isConfiguredFalse() {
        // 空白密钥构造不抛异常（允许服务启动），使用时才报 5200
        assertFalse(new ImConfigCrypto(null).isConfigured());
        assertFalse(new ImConfigCrypto("   ").isConfigured());
    }
}
