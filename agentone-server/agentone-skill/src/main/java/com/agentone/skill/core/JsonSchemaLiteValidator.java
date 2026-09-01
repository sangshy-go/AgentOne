package com.agentone.skill.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 轻量 JSON Schema 校验器（调试器参数预检用）。
 *
 * 只覆盖本系统 Skill inputSchema 实际用到的子集：required + 基本 type
 * （string/number/integer/boolean/object/array）。不引入完整 JSON Schema
 * 校验库：本场景 schema 均为系统内定义的简单 object schema，且校验库的
 * 传递依赖在离线构建环境存在不确定性。
 */
public final class JsonSchemaLiteValidator {

    private JsonSchemaLiteValidator() {
    }

    /**
     * 校验 params 是否符合 schema。
     *
     * @param schema inputSchema（Map 形式的 JSON Schema，可为 null/空 → 直接通过）
     * @param params 用户参数（可为 null，按空对象处理）
     * @return 错误信息列表，空表示通过
     */
    public static List<String> validate(Map<String, Object> schema, Map<String, Object> params) {
        List<String> errors = new ArrayList<>();
        if (schema == null || schema.isEmpty()) {
            return errors;
        }
        Map<String, Object> safeParams = params != null ? params : Map.of();

        // required 兼容 List 与数组两种形态（builtin 描述符用 String[] 字面量构造）
        Object requiredObj = schema.get("required");
        List<?> required = null;
        if (requiredObj instanceof List<?> list) {
            required = list;
        } else if (requiredObj instanceof Object[] array) {
            required = java.util.Arrays.asList(array);
        }
        if (required != null) {
            for (Object field : required) {
                if (field != null && !safeParams.containsKey(field.toString())) {
                    errors.add("缺少必填参数: " + field);
                }
            }
        }

        Object propertiesObj = schema.get("properties");
        if (propertiesObj instanceof Map<?, ?> properties) {
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (!safeParams.containsKey(key) || safeParams.get(key) == null) {
                    continue;   // 缺失交给 required 检查，null 视为不校验类型
                }
                if (entry.getValue() instanceof Map<?, ?> propSchema) {
                    Object type = propSchema.get("type");
                    if (type != null && !typeMatches(type.toString(), safeParams.get(key))) {
                        errors.add("参数 " + key + " 类型应为 " + type
                                + "，实际为 " + javaTypeOf(safeParams.get(key)));
                    }
                }
            }
        }
        return errors;
    }

    private static boolean typeMatches(String jsonType, Object value) {
        return switch (jsonType) {
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map;
            case "array" -> value instanceof List;
            default -> true;   // 未知类型不拦截
        };
    }

    private static String javaTypeOf(Object value) {
        if (value instanceof String) return "string";
        if (value instanceof Integer || value instanceof Long) return "integer";
        if (value instanceof Number) return "number";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof Map) return "object";
        if (value instanceof List) return "array";
        return value.getClass().getSimpleName();
    }
}
