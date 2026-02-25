package com.wpanther.storage.application.controller;

import com.wpanther.storage.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

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
     * Authenticate and generate JWT token.
     * <p>
     * For service-to-service authentication, the username should be
     * the service name (e.g., "service-document-storage-service").
     *
     * @param request Authentication request containing username and password
     * @return JWT token response
     */
    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> authenticate(@RequestBody AuthenticationRequest request) {
        log.info("Authentication request for user: {}", request.username());

        try {
            // Authenticate credentials
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );

            // Load user details
            UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());

            // Generate JWT token
            String token = jwtService.generateToken(userDetails.getUsername());

            log.info("Authentication successful for user: {}", request.username());

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "type", "Bearer",
                    "expiresIn", 86400, // 24 hours in seconds
                    "username", userDetails.getUsername(),
                    "authorities", userDetails.getAuthorities()
            ));

        } catch (Exception e) {
            log.error("Authentication failed for user: {}", request.username(), e);
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Authentication failed",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Refresh JWT token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid authorization header"));
        }

        String oldToken = authHeader.substring(7);
        String username = jwtService.extractUsername(oldToken);

        if (!jwtService.isTokenExpired(oldToken)) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            String newToken = jwtService.generateToken(userDetails.getUsername());

            return ResponseEntity.ok(Map.of(
                    "token", newToken,
                    "type", "Bearer",
                    "expiresIn", 86400
            ));
        }

        return ResponseEntity.status(401).body(Map.of("error", "Token expired"));
    }

    /**
     * Authentication request record
     */
    public record AuthenticationRequest(
            String username,
            String password
    ) {}
}
