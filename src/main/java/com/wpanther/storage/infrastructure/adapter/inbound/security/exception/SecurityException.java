package com.wpanther.storage.infrastructure.adapter.inbound.security.exception;

/**
 * Sealed base class for all security-related exceptions.
 * Provides type-safe exception handling for security failures.
 */
public sealed class SecurityException extends RuntimeException
        permits InvalidTokenException, AuthenticationFailedException, AuthorizationFailedException {

    private final String code;

    public SecurityException(String message, String code) {
        super(message);
        this.code = code;
    }

    public SecurityException(String message, String code, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
