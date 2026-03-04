package com.wpanther.storage.infrastructure.adapter.inbound.rest;

import com.wpanther.storage.infrastructure.security.JwtService;
import com.wpanther.storage.infrastructure.security.exception.AuthenticationFailedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Authentication controller for obtaining JWT tokens.
 * <p>
 * This endpoint is intended for inter-service authentication
 * where services exchange credentials for JWT tokens.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthenticationController {

    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    /**
     * Login endpoint - authenticate and generate JWT token.
     * <p>
     * For service-to-service authentication, the username should be
     * the service name (e.g., "service-document-storage").
     *
     * @param request Authentication request containing username and password
     * @return JWT token response
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request for user: {}", request.username());

        try {
            // Authenticate credentials
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );

            // Load user details
            UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());

            // Generate JWT token and refresh token
            String token = jwtService.generateToken(userDetails.getUsername());
            String refreshToken = jwtService.generateRefreshToken(userDetails.getUsername());

            log.info("Login successful for user: {}", request.username());

            AuthResponse response = new AuthResponse(
                    token,
                    refreshToken,
                    "Bearer",
                    jwtService.getJwtExpiration() / 1000,  // Convert to seconds
                    userDetails.getUsername(),
                    userDetails.getAuthorities().stream()
                            .map(auth -> auth.getAuthority())
                            .toList()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Login failed for user: {}", request.username(), e);
            throw new AuthenticationFailedException("Invalid username or password");
        }
    }

    /**
     * Token endpoint - alias for login (backwards compatibility)
     */
    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> authenticate(@RequestBody LoginRequest request) {
        log.info("Token request for user: {}", request.username());
        AuthResponse response = login(request).getBody();
        return ResponseEntity.ok(Map.of(
                "token", response.token(),
                "type", response.tokenType(),
                "expiresIn", response.expiresIn(),
                "username", response.username(),
                "authorities", response.authorities()
        ));
    }

    /**
     * Refresh JWT token using refresh token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        log.debug("Token refresh request");

        if (request.refreshToken() == null || request.refreshToken().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String oldToken = request.refreshToken();
        String username = jwtService.extractUsername(oldToken);

        if (!jwtService.isTokenValid(oldToken, username)) {
            throw new AuthenticationFailedException("Invalid or expired refresh token");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String newToken = jwtService.generateToken(userDetails.getUsername());
        String newRefreshToken = jwtService.generateRefreshToken(userDetails.getUsername());

        AuthResponse response = new AuthResponse(
                newToken,
                newRefreshToken,
                "Bearer",
                jwtService.getJwtExpiration() / 1000,
                userDetails.getUsername(),
                userDetails.getAuthorities().stream()
                        .map(auth -> auth.getAuthority())
                        .toList()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Validate JWT token and return user information.
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("valid", false, "error", "Missing or invalid authorization header"));
        }

        String token = authHeader.substring(7);
        String username = jwtService.extractUsername(token);

        if (jwtService.isTokenValid(token, username)) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "username", userDetails.getUsername(),
                    "authorities", userDetails.getAuthorities(),
                    "expiresAt", jwtService.extractClaim(token, claims -> claims.getExpiration().toInstant())
            ));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("valid", false, "error", "Invalid or expired token"));
    }

    // DTOs

    /**
     * Login request
     */
    public record LoginRequest(
            String username,
            String password
    ) {}

    /**
     * Refresh token request
     */
    public record RefreshTokenRequest(
            String refreshToken
    ) {}

    /**
     * Authentication response
     */
    public record AuthResponse(
            String token,
            String refreshToken,
            String tokenType,
            long expiresIn,  // seconds
            String username,
            java.util.List<String> authorities
    ) {}
}
