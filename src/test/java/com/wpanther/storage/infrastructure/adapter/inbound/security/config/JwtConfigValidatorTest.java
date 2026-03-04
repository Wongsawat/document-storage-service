package com.wpanther.storage.infrastructure.adapter.inbound.security.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtConfigValidator Tests")
class JwtConfigValidatorTest {

    private static final String VALID_SECRET_256 = generateValidSecret(256);
    private static final String VALID_SECRET_512 = generateValidSecret(512);
    private static final String WEAK_SECRET_128 = generateValidSecret(128);
    private static final String INVALID_SECRET = "not-a-valid-base64-string!@#$%";

    private JwtConfigValidator validator;

    @BeforeEach
    void setUp() {
        // Validator will be created in each test with specific properties
    }

    private static String generateValidSecret(int bits) {
        java.util.Base64.Encoder encoder = java.util.Base64.getEncoder();
        byte[] bytes = new byte[bits / 8];
        new java.util.Random().nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }

    @Nested
    @DisplayName("JWT Secret Validation")
    class SecretValidation {

        @Test
        @DisplayName("Should accept valid 256-bit secret")
        void shouldAccept256BitSecret() {
            // Given
            JwtConfigValidator.JwtProperties props = new JwtConfigValidator.JwtProperties(
                    VALID_SECRET_256,
                    86400000L,
                    604800000L
            );
            validator = new JwtConfigValidator(props);

            // When & Then - should not throw
            validator.validateJwtConfiguration();
        }

        @Test
        @DisplayName("Should accept valid 512-bit secret")
        void shouldAccept512BitSecret() {
            // Given
            JwtConfigValidator.JwtProperties props = new JwtConfigValidator.JwtProperties(
                    VALID_SECRET_512,
                    86400000L,
                    604800000L
            );
            validator = new JwtConfigValidator(props);

            // When & Then - should not throw
            validator.validateJwtConfiguration();
        }

        @Test
        @DisplayName("Should reject null secret")
        void shouldRejectNullSecret() {
            // Given
            JwtConfigValidator.JwtProperties props = new JwtConfigValidator.JwtProperties(
                    null,
                    86400000L,
                    604800000L
            );
            validator = new JwtConfigValidator(props);

            // When & Then
            assertThatThrownBy(() -> validator.validateJwtConfiguration())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT secret is not configured");
        }

        @Test
        @DisplayName("Should reject empty secret")
        void shouldRejectEmptySecret() {
            // Given
            JwtConfigValidator.JwtProperties props = new JwtConfigValidator.JwtProperties(
                    "   ",
                    86400000L,
                    604800000L
            );
            validator = new JwtConfigValidator(props);

            // When & Then
            assertThatThrownBy(() -> validator.validateJwtConfiguration())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT secret is not configured");
        }

        @Test
        @DisplayName("Should reject blank secret")
        void shouldRejectBlankSecret() {
            // Given
            JwtConfigValidator.JwtProperties props = new JwtConfigValidator.JwtProperties(
                    "",
                    86400000L,
                    604800000L
            );
            validator = new JwtConfigValidator(props);

            // When & Then
            assertThatThrownBy(() -> validator.validateJwtConfiguration())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT secret is not configured");
        }

        @Test
        @DisplayName("Should reject weak secret (< 256 bits)")
        void shouldRejectWeakSecret() {
            // Given
            JwtConfigValidator.JwtProperties props = new JwtConfigValidator.JwtProperties(
                    WEAK_SECRET_128,
                    86400000L,
                    604800000L
            );
            validator = new JwtConfigValidator(props);

            // When & Then
            assertThatThrownBy(() -> validator.validateJwtConfiguration())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT secret is too weak")
                    .hasMessageContaining("256 bits");
        }

        @Test
        @DisplayName("Should reject invalid base64 secret")
        void shouldRejectInvalidBase64() {
            // Given
            JwtConfigValidator.JwtProperties props = new JwtConfigValidator.JwtProperties(
                    INVALID_SECRET,
                    86400000L,
                    604800000L
            );
            validator = new JwtConfigValidator(props);

            // When & Then
            assertThatThrownBy(() -> validator.validateJwtConfiguration())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("valid base64-encoded");
        }
    }

    @Nested
    @DisplayName("Expiration Validation")
    class ExpirationValidation {

        @Test
        @DisplayName("Should accept positive expiration values")
        void shouldAcceptPositiveExpiration() {
            // Given
            JwtConfigValidator.JwtProperties props = new JwtConfigValidator.JwtProperties(
                    VALID_SECRET_256,
                    86400000L,   // 24 hours
                    604800000L   // 7 days
            );
            validator = new JwtConfigValidator(props);

            // When & Then - should not throw
            validator.validateJwtConfiguration();
        }

        @Test
        @DisplayName("Should reject zero expiration")
        void shouldRejectZeroExpiration() {
            // Given
            JwtConfigValidator.JwtProperties props = new JwtConfigValidator.JwtProperties(
                    VALID_SECRET_256,
                    0L,
                    604800000L
            );
            validator = new JwtConfigValidator(props);

            // When & Then
            assertThatThrownBy(() -> validator.validateJwtConfiguration())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT expiration must be positive");
        }

        @Test
        @DisplayName("Should reject negative expiration")
        void shouldRejectNegativeExpiration() {
            // Given
            JwtConfigValidator.JwtProperties props = new JwtConfigValidator.JwtProperties(
                    VALID_SECRET_256,
                    -1000L,
                    604800000L
            );
            validator = new JwtConfigValidator(props);

            // When & Then
            assertThatThrownBy(() -> validator.validateJwtConfiguration())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT expiration must be positive");
        }

        @Test
        @DisplayName("Should reject zero refresh expiration")
        void shouldRejectZeroRefreshExpiration() {
            // Given
            JwtConfigValidator.JwtProperties props = new JwtConfigValidator.JwtProperties(
                    VALID_SECRET_256,
                    86400000L,
                    0L
            );
            validator = new JwtConfigValidator(props);

            // When & Then
            assertThatThrownBy(() -> validator.validateJwtConfiguration())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT refresh expiration must be positive");
        }

        @Test
        @DisplayName("Should reject negative refresh expiration")
        void shouldRejectNegativeRefreshExpiration() {
            // Given
            JwtConfigValidator.JwtProperties props = new JwtConfigValidator.JwtProperties(
                    VALID_SECRET_256,
                    86400000L,
                    -1000L
            );
            validator = new JwtConfigValidator(props);

            // When & Then
            assertThatThrownBy(() -> validator.validateJwtConfiguration())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT refresh expiration must be positive");
        }

        @Test
        @DisplayName("Should accept refresh expiration longer than access expiration")
        void shouldAcceptRefreshLongerThanAccess() {
            // Given - refresh token should live longer
            JwtConfigValidator.JwtProperties props = new JwtConfigValidator.JwtProperties(
                    VALID_SECRET_256,
                    3600000L,    // 1 hour access
                    604800000L   // 7 days refresh
            );
            validator = new JwtConfigValidator(props);

            // When & Then - should not throw
            validator.validateJwtConfiguration();
        }

        @Test
        @DisplayName("Should accept refresh expiration equal to access expiration")
        void shouldAcceptRefreshEqualToAccess() {
            // Given
            long sameExpiration = 86400000L;
            JwtConfigValidator.JwtProperties props = new JwtConfigValidator.JwtProperties(
                    VALID_SECRET_256,
                    sameExpiration,
                    sameExpiration
            );
            validator = new JwtConfigValidator(props);

            // When & Then - should not throw
            validator.validateJwtConfiguration();
        }
    }

    @Nested
    @DisplayName("Error Messages")
    class ErrorMessages {

        @Test
        @DisplayName("Should provide helpful message for weak secret")
        void shouldProvideHelpfulWeakSecretMessage() {
            // Given
            JwtConfigValidator.JwtProperties props = new JwtConfigValidator.JwtProperties(
                    WEAK_SECRET_128,
                    86400000L,
                    604800000L
            );
            validator = new JwtConfigValidator(props);

            // When
            Exception exception = catchException(() -> validator.validateJwtConfiguration());

            // Then
            assertThat(exception)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("openssl rand -base64 64");
        }

        @Test
        @DisplayName("Should provide helpful message for invalid base64")
        void shouldProvideHelpfulInvalidBase64Message() {
            // Given
            JwtConfigValidator.JwtProperties props = new JwtConfigValidator.JwtProperties(
                    INVALID_SECRET,
                    86400000L,
                    604800000L
            );
            validator = new JwtConfigValidator(props);

            // When
            Exception exception = catchException(() -> validator.validateJwtConfiguration());

            // Then
            assertThat(exception)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("openssl rand -base64 64");
        }

        @Test
        @DisplayName("Should include current secret length in weak secret message")
        void shouldIncludeCurrentLengthInMessage() {
            // Given - 128-bit secret decodes to 16 bytes
            JwtConfigValidator.JwtProperties props = new JwtConfigValidator.JwtProperties(
                    WEAK_SECRET_128,
                    86400000L,
                    604800000L
            );
            validator = new JwtConfigValidator(props);

            // When
            Exception exception = catchException(() -> validator.validateJwtConfiguration());

            // Then
            assertThat(exception.getMessage())
                    .contains("16")
                    .contains("32");  // Required 32 bytes
        }
    }

    // Helper method for exception assertions
    private Exception catchException(Runnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (Exception e) {
            return e;
        }
    }
}
