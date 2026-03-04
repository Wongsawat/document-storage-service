package com.wpanther.storage.infrastructure.adapter.inbound.security.exception;

/**
 * Thrown when authentication fails (missing credentials, invalid username/password).
 */
public final class AuthenticationFailedException extends SecurityException {

    public AuthenticationFailedException(String message) {
        super(message, "AUTHENTICATION_FAILED");
    }

    public AuthenticationFailedException(String message, Throwable cause) {
        super(message, "AUTHENTICATION_FAILED", cause);
    }
}
