package com.flexcore.core.exception;

import lombok.Getter;

@Getter
public class TooManyRequestsException extends RuntimeException {

    private final String code;
    private final transient Object[] args;

    public TooManyRequestsException(String code, Object... args) {
        super(code);
        this.code = code;
        this.args = args;
    }
}
