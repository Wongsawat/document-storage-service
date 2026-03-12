package com.wpanther.storage.infrastructure.adapter.in.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing JWT token revocation/blacklist.
 * <p>
 * Uses Caffeine cache for efficient in-memory storage of revoked tokens
 * with automatic expiration to prevent unbounded growth.
 * <p>
 * Use cases:
 * - Forced logout after password change
 * - Compromised token handling
 * - User session invalidation
 * - Administrative token revocation
 */
@Service
@Slf4j
public class TokenBlacklistService {

    private final Cache<String, Boolean> revokedTokens;

    /**
     * Initialize token blacklist with configurable parameters.
     *
     * @param maxSize       Maximum number of revoked tokens to store (default: 10,000)
     * @param expireHours   Hours until a revoked token entry expires (default: 168 hours = 7 days)
     */
    public TokenBlacklistService(
            @Value("${app.security.jwt.blacklist.size:10000}") long maxSize,
            @Value("${app.security.jwt.blacklist.expire-hours:168}") long expireHours
    ) {
        this.revokedTokens = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofHours(expireHours))
                .build();

        log.info("TokenBlacklistService initialized with maxSize={}, expireHours={}", maxSize, expireHours);
    }

    /**
     * Revoke a JWT token by adding it to the blacklist.
     * <p>
     * The token will remain in the blacklist until it expires based on
     * the configured expiration time.
     *
     * @param token The JWT token to revoke
     */
    public void revokeToken(String token) {
        log.debug("Revoking token");
        revokedTokens.put(token, true);
        log.info("Token revoked successfully");
    }

    /**
     * Check if a token has been revoked.
     *
     * @param token The JWT token to check
     * @return true if the token is revoked, false otherwise
     */
    public boolean isRevoked(String token) {
        Boolean revoked = revokedTokens.getIfPresent(token);
        if (revoked != null && revoked) {
            log.debug("Token is revoked");
            return true;
        }
        return false;
    }

    /**
     * Get the current size of the token blacklist.
     * <p>
     * Useful for monitoring and metrics.
     *
     * @return The number of tokens currently in the blacklist
     */
    public long size() {
        return revokedTokens.estimatedSize();
    }

    /**
     * Clear all revoked tokens from the blacklist.
     * <p>
     * Use with caution - this will allow all previously revoked tokens
     * to be used again until their natural expiration.
     */
    public void clear() {
        log.warn("Clearing all revoked tokens from blacklist");
        revokedTokens.invalidateAll();
    }
}
