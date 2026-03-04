package com.wpanther.storage.domain.model;

import java.time.Instant;

/**
 * Value object representing an authentication token.
 */
public record AuthToken(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    Instant issuedAt
) {
    public static AuthToken of(String accessToken, String refreshToken, long expiresIn) {
        return new AuthToken(
            accessToken,
            refreshToken,
            "Bearer",
            expiresIn,
            Instant.now()
        );
    }
}
