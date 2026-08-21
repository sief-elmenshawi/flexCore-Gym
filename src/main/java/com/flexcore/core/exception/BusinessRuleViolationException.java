package com.flexcore.core.exception;

import lombok.Getter;

@Getter
public class BusinessRuleViolationException extends RuntimeException {

    private final String code;
    private final transient Object[] args;

    public BusinessRuleViolationException(String code, Object... args) {
        super(code);
        this.code = code;
        this.args = args;
    }
}
