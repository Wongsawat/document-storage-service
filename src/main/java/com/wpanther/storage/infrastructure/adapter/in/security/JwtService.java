package com.wpanther.storage.infrastructure.adapter.in.security;

import com.wpanther.storage.infrastructure.adapter.in.security.exception.SecurityException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * Service for JWT token generation and validation.
 * Uses HS256 algorithm with configurable secret key.
 * <p>
 * This service integrates with {@link TokenBlacklistService} to support token revocation.
 */
@Service
@Slf4j
public class JwtService {

    private final TokenBlacklistService tokenBlacklistService;
    private final String secretKey;
    private final long jwtExpiration;
    private final long refreshExpiration;

    /**
     * Constructor with dependency injection.
     *
     * @param secretKey          JWT secret key (base64-encoded), MUST be configured via JWT_SECRET env variable
     * @param jwtExpiration       JWT access token expiration in milliseconds (default: 24 hours)
     * @param refreshExpiration   JWT refresh token expiration in milliseconds (default: 7 days)
     * @param tokenBlacklistService Token blacklist service for revocation support
     */
    public JwtService(
            @Value("${app.security.jwt.secret}") String secretKey,
            @Value("${app.security.jwt.expiration:86400000}") long jwtExpiration,
            @Value("${app.security.jwt.refresh-expiration:604800000}") long refreshExpiration,
            TokenBlacklistService tokenBlacklistService) {
        this.secretKey = secretKey;
        this.jwtExpiration = jwtExpiration;
        this.refreshExpiration = refreshExpiration;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /**
     * Extract username from token
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract specific claim from token
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Generate access token for user
     */
    public String generateToken(String username) {
        return generateToken(Map.of(), username);
    }

    /**
     * Generate access token with additional claims
     */
    public String generateToken(Map<String, Object> extraClaims, String username) {
        return buildToken(extraClaims, username, jwtExpiration);
    }

    /**
     * Generate refresh token
     */
    public String generateRefreshToken(String username) {
        return buildToken(Map.of(), username, refreshExpiration);
    }

    /**
     * Validate token against username.
     * <p>
     * Checks if the token:
     * - Has not been revoked
     * - Has the correct username
     * - Has not expired
     *
     * @param token    the JWT token to validate
     * @param username the expected username
     * @return true if the token is valid, false otherwise
     */
    public boolean isTokenValid(String token, String username) {
        try {
            // Check if token is revoked first
            if (tokenBlacklistService.isRevoked(token)) {
                log.debug("Token is revoked for user: {}", username);
                return false;
            }

            final String extractedUsername = extractUsername(token);
            return extractedUsername.equals(username) && !isTokenExpired(token);
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(Date.from(Instant.now()));
        } catch (SecurityException e) {
            // If we can't parse the token (e.g., expired), check if it's an expired token exception
            if (e.getCause() instanceof ExpiredJwtException) {
                return true;
            }
            throw e;
        }
    }

    /**
     * Extract expiration date from token
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Build JWT token with specified expiration
     */
    private String buildToken(Map<String, Object> extraClaims, String username, long expiration) {
        Instant now = Instant.now();
        Instant exp = now.plus(expiration, ChronoUnit.MILLIS);

        log.debug("Generating token for user: {}, expires at: {}", username, exp);

        return Jwts.builder()
                .claims(extraClaims)
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(getSignInKey())
                .compact();
    }

    /**
     * Extract all claims from token
     */
    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSignInKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("Failed to parse JWT token: {}", e.getMessage());
            throw new SecurityException("Invalid JWT token", "INVALID_JWT", e);
        }
    }

    /**
     * Get signing key from base64-encoded secret
     */
    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Get JWT expiration time in milliseconds
     */
    public long getJwtExpiration() {
        return jwtExpiration;
    }
}
