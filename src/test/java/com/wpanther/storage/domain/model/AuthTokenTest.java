package com.wpanther.storage.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthTokenTest {
    @Test
    void authToken_createsSuccessfully() {
        AuthToken token = new AuthToken("access", "refresh", "Bearer", 3600, java.time.Instant.now());
        assertThat(token.accessToken()).isEqualTo("access");
    }
    @Test
    void factoryMethod_createsToken() {
        AuthToken token = AuthToken.of("access", "refresh", 3600);
        assertThat(token.tokenType()).isEqualTo("Bearer");
    }
}
