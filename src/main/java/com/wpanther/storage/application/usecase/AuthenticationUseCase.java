package com.wpanther.storage.application.usecase;

import com.wpanther.storage.domain.model.AuthToken;

/**
 * Inbound port for authentication operations.
 * Implemented by AuthenticationDomainService.
 * Called by AuthenticationController and JwtAuthenticationAdapter.
 */
public interface AuthenticationUseCase {
    AuthToken authenticate(String username, String password);
    AuthToken refreshToken(String refreshToken);
    void logout(String token);
    boolean validateToken(String token);
    String extractUsername(String token);
}
