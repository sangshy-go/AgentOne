package com.agentone.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体
 * 格式: { code, message, data }
 */
@Data
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;

    private Result() {}

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.setCode(0);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    // success 是 ok 的别名，保持向后兼容
    public static <T> Result<T> success() {
        return ok(null);
    }

    public static <T> Result<T> success(T data) {
        return ok(data);
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return fail(resultCode.getCode(), resultCode.getMessage());
    }
}
