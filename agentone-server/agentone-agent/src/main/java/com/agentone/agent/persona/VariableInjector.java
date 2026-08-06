package com.agentone.agent.persona;

import com.agentone.common.context.RuntimeContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 变量注入器
 * 将 AGENTS.md 中的 {{variable}} 替换为实际值
 *
 * 内置变量:
 * - {{user_name}}   当前用户昵称
 * - {{user_id}}     当前用户 ID
 * - {{date}}        当前日期（yyyy-MM-dd）
 * - {{workspace}}   当前工作空间 ID
 * - {{datetime}}    当前日期时间
 */
@Component
public class VariableInjector {

    // 支持中文变量名：[\w一-龥]+
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([\\w\\u4e00-\\u9fa5]+)}}");

    /**
     * 注入变量到文本中
     *
     * @param text       包含 {{variable}} 的模板文本
     * @param extraVars  额外自定义变量
     * @return 替换后的文本
     */
    public String inject(String text, Map<String, String> extraVars) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // 内置变量（使用 LocalDateTime.now() 保证原子性）
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> vars = new HashMap<>();
        vars.put("date", now.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        vars.put("datetime", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        String userId = RuntimeContext.getUserId();
        vars.put("user_id", userId != null ? userId : "");

        String workspaceId = RuntimeContext.getWorkspaceId();
        vars.put("workspace", workspaceId != null ? workspaceId : "");

        // 额外变量覆盖
        if (extraVars != null) {
            vars.putAll(extraVars);
        }

        // 替换
        Matcher matcher = VAR_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = vars.getOrDefault(varName, matcher.group(0)); // 未识别的变量保留原样
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    public String inject(String text) {
        return inject(text, null);
    }
}
