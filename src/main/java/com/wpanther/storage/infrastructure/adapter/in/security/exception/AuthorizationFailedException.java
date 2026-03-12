package com.wpanther.storage.infrastructure.adapter.in.security.exception;

/**
 * Thrown when authorization fails (insufficient permissions, forbidden access).
 */
public final class AuthorizationFailedException extends SecurityException {

    private final String requiredRole;
    private final String requiredPermission;

    public AuthorizationFailedException(String message, String requiredRole) {
        super(message, "AUTHORIZATION_FAILED");
        this.requiredRole = requiredRole;
        this.requiredPermission = null;
    }

    public AuthorizationFailedException(String message, String requiredPermission, boolean isPermission) {
        super(message, "AUTHORIZATION_FAILED");
        this.requiredRole = null;
        this.requiredPermission = requiredPermission;
    }

    public String getRequiredRole() {
        return requiredRole;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }
}
