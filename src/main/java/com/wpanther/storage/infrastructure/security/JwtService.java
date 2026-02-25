package com.wpanther.storage.infrastructure.security;

import com.wpanther.storage.infrastructure.security.exception.SecurityException;
import io.jsonwebtoken.Claims;
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
 */
@Service
@Slf4j
public class JwtService {

    @Value("${app.security.jwt.secret:404E635266556A586E3272354E39423F4428472B4B6250645367566B59703373367639792F423F4528482B4D6251655468576D5A7134743777397A24432646294A404E635266556A586E3272357538782F413F4428472B4B6250645367566B59703373367639792F425A452D4A614E645267556B58703273357538782F413F4428472B4B6250645367533879")
    private String secretKey;

    @Value("${app.security.jwt.expiration:86400000}") // 24 hours default
    private long jwtExpiration;

    @Value("${app.security.jwt.refresh-expiration:604800000}") // 7 days default
    private long refreshExpiration;

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
     * Validate token against username
     */
    public boolean isTokenValid(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username)) && !isTokenExpired(token);
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(Date.from(Instant.now()));
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
}
