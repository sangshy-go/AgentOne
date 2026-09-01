package com.agentone.skill.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JsonSchemaLiteValidator 单测：required 与基本 type 校验（课题③调试器参数预检）。
 */
class JsonSchemaLiteValidatorTest {

    private Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "required", List.of("city"),
                "properties", Map.of(
                        "city", Map.of("type", "string"),
                        "days", Map.of("type", "integer"),
                        "score", Map.of("type", "number"),
                        "notify", Map.of("type", "boolean"),
                        "filter", Map.of("type", "object"),
                        "tags", Map.of("type", "array")));
    }

    @Test
    void validate_nullSchema_passes() {
        assertTrue(JsonSchemaLiteValidator.validate(null, Map.of("any", "thing")).isEmpty());
    }

    @Test
    void validate_emptySchema_passes() {
        assertTrue(JsonSchemaLiteValidator.validate(Map.of(), Map.of()).isEmpty());
    }

    @Test
    void validate_requiredMissing_reportsError() {
        List<String> errors = JsonSchemaLiteValidator.validate(schema(), Map.of());
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("city"), "错误信息应指出缺失字段");
    }

    @Test
    void validate_nullParams_treatedAsEmpty() {
        List<String> errors = JsonSchemaLiteValidator.validate(schema(), null);
        assertEquals(1, errors.size());
    }

    @Test
    void validate_allCorrectTypes_passes() {
        Map<String, Object> params = Map.of(
                "city", "北京",
                "days", 3,
                "score", 0.5,
                "notify", true,
                "filter", Map.of("k", "v"),
                "tags", List.of("a"));
        assertTrue(JsonSchemaLiteValidator.validate(schema(), params).isEmpty());
    }

    @Test
    void validate_typeMismatch_reportsError() {
        List<String> errors = JsonSchemaLiteValidator.validate(
                schema(), Map.of("city", 123));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("city"));
        assertTrue(errors.get(0).contains("string"), "错误信息应说明期望类型");
    }

    @Test
    void validate_multipleErrors_allReported() {
        // days 缺失 + city 类型错 + notify 类型错
        List<String> errors = JsonSchemaLiteValidator.validate(
                schema(), Map.of("city", 123, "notify", "yes"));
        assertEquals(2, errors.size(), "city 类型错 + notify 类型错（days 不在 required 里不报缺失）");
    }

    @Test
    void validate_integerAcceptsLong_passes() {
        assertTrue(JsonSchemaLiteValidator.validate(
                schema(), Map.of("city", "北京", "days", 7L)).isEmpty());
    }

    @Test
    void validate_integerRejectsDouble_reportsError() {
        List<String> errors = JsonSchemaLiteValidator.validate(
                schema(), Map.of("city", "北京", "days", 2.5));
        assertEquals(1, errors.size());
    }

    @Test
    void validate_nullValue_skipsTypeCheck() {
        // null 值不做类型校验，只剩 required 的缺失错误
        List<String> errors = JsonSchemaLiteValidator.validate(
                schema(), java.util.Collections.singletonMap("days", null));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("city"));
    }

    @Test
    void validate_unknownParam_allowed() {
        assertTrue(JsonSchemaLiteValidator.validate(
                schema(), Map.of("city", "北京", "unknownField", "x")).isEmpty());
    }

    @Test
    void validate_requiredAsArray_alsoEnforced() {
        // builtin 描述符以 String[] 字面量构造 required，校验器需兼容数组形态
        Map<String, Object> schema = Map.of(
                "type", "object",
                "required", new String[]{"code"},
                "properties", Map.of("code", Map.of("type", "string")));
        List<String> errors = JsonSchemaLiteValidator.validate(schema, Map.of());
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("code"));
    }

    @Test
    void validate_unknownType_notBlocked() {
        Map<String, Object> schema = Map.of(
                "properties", Map.of("x", Map.of("type", "custom-type")));
        assertTrue(JsonSchemaLiteValidator.validate(schema, Map.of("x", "anything")).isEmpty());
    }
}
