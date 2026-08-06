package com.agentone.common.util;

/**
 * Token 计数器
 * 提供近似 Token 估算（基于字符数和语言比例）
 *
 * 原理：
 * - 中文：约 1 个汉字 = 1-2 个 token（取平均 1.5）
 * - 英文：约 4 个字符 = 1 个 token
 * - 混合文本：按中英文比例加权计算
 */
public class TokenCounter {

    /**
     * 估算文本的 Token 数量
     *
     * @param text 输入文本
     * @return 估算的 Token 数
     */
    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int chineseCount = 0;
        int totalLength = text.length();

        // 统计中文字符数
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseCount++;
            }
        }

        int englishCount = totalLength - chineseCount;

        // 中文：1.5 token/字
        // 英文：0.25 token/字符（4字符 = 1 token）
        double tokens = chineseCount * 1.5 + englishCount * 0.25;

        return (int) Math.ceil(tokens);
    }

    /**
     * 快速估算（简化版，不区分中英文）
     * 适用于对精度要求不高的场景
     *
     * @param text 输入文本
     * @return 估算的 Token 数
     */
    public static int estimateFast(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // 平均估算：1 token ≈ 2 字符
        return (int) Math.ceil(text.length() * 0.5);
    }
}
