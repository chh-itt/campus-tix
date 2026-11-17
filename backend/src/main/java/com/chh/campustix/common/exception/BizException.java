package com.chh.campustix.common.exception;

import com.chh.campustix.common.api.ResultCode;
import lombok.Getter;

@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(ResultCode.ERROR.getCode(), message);
    }

    public BizException(ResultCode rc) {
        this(rc.getCode(), rc.getMessage());
    }

    public BizException(ResultCode rc, String message) {
        this(rc.getCode(), message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
