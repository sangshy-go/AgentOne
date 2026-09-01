package com.agentone.im.adapter;

import com.agentone.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WecomCrypto 单测（官方 WXBizMsgCrypt 协议）：
 * 加解密往返、receiveId 校验 5205、签名校验 5205、非法 encodingAesKey 5204。
 * encodingAesKey 固定取 bytes(0..31) 的 base64 去尾 "="，保证可复现
 */
class WecomCryptoTest {

    private static final String AES_KEY_43 = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";
    private static final String TOKEN = "test-token";
    private static final String CORP_ID = "corp-a";

    private WecomCrypto crypto() {
        return new WecomCrypto(TOKEN, AES_KEY_43, CORP_ID);
    }

    @Test
    void encryptDecrypt_roundtrip_returnsOriginal() {
        WecomCrypto c = crypto();
        String xml = "<xml><MsgType><![CDATA[text]]></MsgType><Content><![CDATA[你好，世界]]></Content></xml>";
        assertEquals(xml, c.decrypt(c.encrypt(xml)));
    }

    @Test
    void decrypt_receiveIdMismatch_throws5205() {
        String encrypted = crypto().encrypt("hello");
        WecomCrypto otherCorp = new WecomCrypto(TOKEN, AES_KEY_43, "corp-b");
        BusinessException ex = assertThrows(BusinessException.class, () -> otherCorp.decrypt(encrypted));
        assertEquals(5205, ex.getCode());
    }

    @Test
    void decrypt_tamperedCiphertext_throws5205() {
        String encrypted = crypto().encrypt("hello");
        // 翻转中间一个字符，破坏密文
        char[] chars = encrypted.toCharArray();
        chars[20] = chars[20] == 'A' ? 'B' : 'A';
        BusinessException ex = assertThrows(BusinessException.class, () -> crypto().decrypt(new String(chars)));
        assertEquals(5205, ex.getCode());
    }

    @Test
    void verifySignature_validSignature_passes() {
        String signature = WecomCrypto.sha1Sign(TOKEN, "1700000000", "nonce-1", "encrypt-data");
        assertDoesNotThrow(() -> crypto().verifySignature(signature, "1700000000", "nonce-1", "encrypt-data"));
    }

    @Test
    void verifySignature_wrongSignature_throws5205() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> crypto().verifySignature("bad-signature", "1700000000", "nonce-1", "encrypt-data"));
        assertEquals(5205, ex.getCode());
    }

    @Test
    void sha1Sign_knownVector_matchesIndependentImplementation() {
        // python: sha1("".join(sorted(["test-token","1700000000","nonce-1","encrypt-data"])))
        assertEquals("361fcbf20acfce67fab048495147217a43563dbe",
                WecomCrypto.sha1Sign(TOKEN, "1700000000", "nonce-1", "encrypt-data"));
    }

    @Test
    void constructor_invalidEncodingAesKey_throws5204() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> new WecomCrypto(TOKEN, "tooshort", CORP_ID));
        assertEquals(5204, ex.getCode());
    }

    @Test
    void buildReplyEnvelope_containsEncryptSignatureTimestampNonce() {
        String envelope = crypto().buildReplyEnvelope("ENCRYPTED", "1700000000", "nonce-1");
        assertTrue(envelope.contains("<Encrypt><![CDATA[ENCRYPTED]]></Encrypt>"));
        assertTrue(envelope.contains("<MsgSignature><![CDATA["
                + WecomCrypto.sha1Sign(TOKEN, "1700000000", "nonce-1", "ENCRYPTED") + "]]></MsgSignature>"));
        assertTrue(envelope.contains("<TimeStamp>1700000000</TimeStamp>"));
        assertTrue(envelope.contains("<Nonce><![CDATA[nonce-1]]></Nonce>"));
    }
}
