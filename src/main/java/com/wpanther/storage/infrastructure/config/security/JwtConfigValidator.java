package com.wpanther.storage.infrastructure.config.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * Validates JWT configuration at application startup.
 * Fails fast if security configuration is invalid.
 */
@Component
@Slf4j
public class JwtConfigValidator {

    private final JwtProperties jwtProperties;

    public JwtConfigValidator(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateJwtConfiguration() {
        log.info("Validating JWT configuration...");

        // Validate JWT secret
        String secret = jwtProperties.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT secret is not configured. Please set JWT_SECRET environment variable. " +
                "The secret must be at least 256 bits (32 bytes) when base64-decoded."
            );
        }

        // Validate secret length (after base64 decoding)
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            if (decoded.length < 32) {
                throw new IllegalStateException(
                    "JWT secret is too weak. Must be at least 256 bits (32 bytes) after base64 decoding. " +
                    "Current length: " + decoded.length + " bytes. " +
                    "Generate a secure secret with: openssl rand -base64 64"
                );
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "JWT secret must be a valid base64-encoded string. " +
                "Generate a secure secret with: openssl rand -base64 64"
            );
        }

        // Validate expiration times
        if (jwtProperties.expiration() <= 0) {
            throw new IllegalStateException("JWT expiration must be positive");
        }

        if (jwtProperties.refreshExpiration() <= 0) {
            throw new IllegalStateException("JWT refresh expiration must be positive");
        }

        log.info("JWT configuration validated successfully (token expiration: {} ms, refresh: {} ms)",
                jwtProperties.expiration(), jwtProperties.refreshExpiration());
    }

    /**
     * JWT configuration properties
     */
    public record JwtProperties(
            String secret,
            long expiration,
            long refreshExpiration
    ) {}
}
