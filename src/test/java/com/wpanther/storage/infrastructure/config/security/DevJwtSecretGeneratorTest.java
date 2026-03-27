package com.wpanther.storage.infrastructure.config.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DevJwtSecretGenerator Tests")
class DevJwtSecretGeneratorTest {

    private static final String PLACEHOLDER = "GENERATE_DEV_SECRET";
    private static final String REAL_SECRET = "dGhpcy1pcy1hLXZhbGlkLWJhc2U2NC1zZWNyZXQtdGhhdC1pcy1sYXJnZS1lbm91Z2gtdG8tcGFzcy12YWxpZGF0aW9uLWFuZC1zaG91bGQtYmUtZ2VuZXJhdGVkLWluLXByb2R1Y3Rpb24=";
    private static final String PROPERTY_NAME = "app.security.jwt.secret";

    private MockEnvironment environment;
    private DevJwtSecretGenerator generator;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
    }

    @Nested
    @DisplayName("Placeholder Detection")
    class PlaceholderDetection {

        @Test
        @DisplayName("Should detect GENERATE_DEV_SECRET placeholder")
        void shouldDetectPlaceholder() {
            createGenerator();
            assertThat(generator.isPlaceholderValue(PLACEHOLDER)).isTrue();
        }

        @Test
        @DisplayName("Should not detect real secret as placeholder")
        void shouldNotDetectRealSecretAsPlaceholder() {
            createGenerator();
            assertThat(generator.isPlaceholderValue(REAL_SECRET)).isFalse();
        }

        @Test
        @DisplayName("Should not detect null as placeholder")
        void shouldNotDetectNullAsPlaceholder() {
            createGenerator();
            assertThat(generator.isPlaceholderValue(null)).isFalse();
        }

        @Test
        @DisplayName("Should not detect empty string as placeholder")
        void shouldNotDetectEmptyAsPlaceholder() {
            createGenerator();
            assertThat(generator.isPlaceholderValue("")).isFalse();
        }
    }

    @Nested
    @DisplayName("Secret Generation")
    class SecretGeneration {

        @Test
        @DisplayName("Should generate valid base64-encoded secret")
        void shouldGenerateValidBase64Secret() {
            createGenerator();
            String secret = generator.generateSecureSecret();

            assertThat(secret).isNotNull();
            assertThat(Base64.getDecoder().decode(secret)).isNotNull();
        }

        @Test
        @DisplayName("Should generate secret meeting 256-bit minimum requirement")
        void shouldGenerate256BitSecret() {
            createGenerator();
            String secret = generator.generateSecureSecret();
            byte[] decoded = Base64.getDecoder().decode(secret);

            assertThat(decoded.length).isGreaterThanOrEqualTo(32);
        }

        @Test
        @DisplayName("Should generate unique secrets on each call")
        void shouldGenerateUniqueSecrets() {
            createGenerator();
            String secret1 = generator.generateSecureSecret();
            String secret2 = generator.generateSecureSecret();

            assertThat(secret1).isNotEqualTo(secret2);
        }

        @Test
        @DisplayName("Should generate consistent 64-byte secret")
        void shouldGenerate64ByteSecret() {
            createGenerator();
            String secret = generator.generateSecureSecret();
            byte[] decoded = Base64.getDecoder().decode(secret);

            assertThat(decoded.length).isEqualTo(64);
        }
    }

    @Nested
    @DisplayName("Environment Override")
    class EnvironmentOverride {

        @Test
        @DisplayName("Should override property when placeholder detected")
        void shouldOverrideWhenPlaceholder() {
            environment.setProperty(PROPERTY_NAME, PLACEHOLDER);
            environment.setActiveProfiles("dev");

            createGenerator();

            // After generator creation, check the environment
            String overridden = environment.getProperty(PROPERTY_NAME);
            assertThat(overridden).isNotNull();
            assertThat(overridden).isNotEqualTo(PLACEHOLDER);
            byte[] decoded = Base64.getDecoder().decode(overridden);
            assertThat(decoded.length).isEqualTo(64);
        }

        @Test
        @DisplayName("Should not override real secret value")
        void shouldNotOverrideRealSecret() {
            environment.setProperty(PROPERTY_NAME, REAL_SECRET);
            environment.setActiveProfiles("dev");

            createGenerator();

            // Real secret should remain unchanged
            assertThat(environment.getProperty(PROPERTY_NAME)).isEqualTo(REAL_SECRET);
        }

        @Test
        @DisplayName("Should not override when dev profile not active")
        void shouldNotOverrideWhenDevNotActive() {
            environment.setProperty(PROPERTY_NAME, PLACEHOLDER);
            environment.setActiveProfiles("prod", "test");

            createGenerator();

            // Should not override when dev profile not active
            assertThat(environment.getProperty(PROPERTY_NAME)).isEqualTo(PLACEHOLDER);
        }

        @Test
        @DisplayName("Should not override when secret is null")
        void shouldNotOverrideWhenSecretIsNull() {
            environment.setActiveProfiles("dev");
            // Don't set the property - will be null

            createGenerator();

            // Should not override when secret is null
            assertThat(environment.getProperty(PROPERTY_NAME)).isNull();
        }

        @Test
        @DisplayName("Generated secret should be valid base64")
        void shouldGenerateValidBase64SecretInOverride() {
            environment.setProperty(PROPERTY_NAME, PLACEHOLDER);
            environment.setActiveProfiles("dev");

            createGenerator();

            String secret = environment.getProperty(PROPERTY_NAME);
            assertThat(secret).isNotNull();
            // Should be valid base64 (no exception thrown)
            assertThat(Base64.getDecoder().decode(secret)).isNotNull();
        }
    }

    private void createGenerator() {
        generator = new DevJwtSecretGenerator(environment);
    }
}
