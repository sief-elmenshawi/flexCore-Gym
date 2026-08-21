package com.flexcore.core.exception;

import lombok.Getter;

@Getter
public class DuplicateResourceException extends RuntimeException {

    private final String code;
    private final transient Object[] args;

    public DuplicateResourceException(String code, Object... args) {
        super(code);
        this.code = code;
        this.args = args;
    }
}
