package com.agentone.skill.executor;

import com.agentone.common.context.Context;
import com.agentone.skill.core.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * 代码执行 Skill（简化版）
 * 支持 JavaScript 代码执行（使用 Nashorn/GraalVM）
 *
 * ⚠️ 注意：这是简化版，生产环境应使用 Docker 沙箱隔离
 *
 * 输入参数:
 * - language: 编程语言（目前支持 javascript）
 * - code: 代码内容
 * - variables: 输入变量（可选）
 */
@Slf4j
@Component
public class CodeExecuteSkill implements SkillExecutor {

    private static final String SKILL_ID = "builtin-code-execute";

    @Override
    public SkillResult execute(SkillInvocation invocation, Context context) {
        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> params = invocation.getParams();
            String language = getStringParam(params, "language", "javascript").toLowerCase();
            String code = getStringParam(params, "code", null);

            if (code == null || code.isBlank()) {
                return SkillResult.failure("代码不能为空", System.currentTimeMillis() - startTime);
            }

            // 目前只支持 JavaScript
            if (!"javascript".equals(language) && !"js".equals(language)) {
                return SkillResult.failure("暂不支持 " + language + "，目前仅支持 JavaScript",
                        System.currentTimeMillis() - startTime);
            }

            return executeJavaScript(code, params, startTime);

        } catch (Exception e) {
            log.error("代码执行失败: {}", e.getMessage());
            return SkillResult.failure("代码执行失败: " + e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    private SkillResult executeJavaScript(String code, Map<String, Object> params, long startTime) {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("js");

        if (engine == null) {
            // 尝试 GraalVM JavaScript
            engine = manager.getEngineByName("graal.js");
        }

        if (engine == null) {
            return SkillResult.failure("JavaScript 引擎不可用，请检查 JDK 版本",
                    System.currentTimeMillis() - startTime);
        }

        try {
            // 设置输出捕获
            StringWriter outputWriter = new StringWriter();
            engine.getContext().setWriter(outputWriter);
            engine.getContext().setErrorWriter(outputWriter);

            // 设置输入变量
            @SuppressWarnings("unchecked")
            Map<String, Object> variables = (Map<String, Object>) params.get("variables");
            if (variables != null) {
                SimpleBindings bindings = new SimpleBindings();
                bindings.putAll(variables);
                engine.setBindings(bindings, javax.script.ScriptContext.ENGINE_SCOPE);
            }

            // 执行代码
            Object result = engine.eval(code);

            // 构建返回数据
            Map<String, Object> data = new HashMap<>();
            data.put("result", result != null ? result.toString() : null);
            data.put("output", outputWriter.toString());
            data.put("language", "javascript");

            return SkillResult.success(data, System.currentTimeMillis() - startTime);

        } catch (ScriptException e) {
            log.error("JavaScript 执行错误: {}", e.getMessage());
            return SkillResult.failure("代码语法错误: " + e.getMessage(),
                    System.currentTimeMillis() - startTime);
        }
    }

    @Override
    public SkillDescriptor getDescriptor() {
        return SkillDescriptor.builder()
                .id(SKILL_ID)
                .name("代码执行")
                .description("执行 JavaScript 代码（简化版，生产环境应使用 Docker 沙箱）")
                .type("builtin")
                .version("1.0.0")
                .source("agentone")
                .enabled(true)
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "language", Map.of("type", "string", "enum", new String[]{"javascript", "js"}),
                                "code", Map.of("type", "string", "description", "代码内容"),
                                "variables", Map.of("type", "object", "description", "输入变量")
                        ),
                        "required", new String[]{"code"}
                ))
                .build();
    }

    private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
