package com.agentone.common.exception;

import com.agentone.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常 - 可预期的业务错误
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
