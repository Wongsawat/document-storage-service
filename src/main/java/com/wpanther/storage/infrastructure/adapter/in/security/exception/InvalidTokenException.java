package com.wpanther.storage.infrastructure.adapter.in.security.exception;

/**
 * Thrown when a JWT token is invalid or malformed.
 */
public final class InvalidTokenException extends SecurityException {

    private final String token;

    public InvalidTokenException(String message, String token) {
        super(message, "INVALID_TOKEN");
        this.token = token;
    }

    public InvalidTokenException(String message, String token, Throwable cause) {
        super(message, "INVALID_TOKEN", cause);
        this.token = token;
    }

    public String getToken() {
        return token;
    }
}
