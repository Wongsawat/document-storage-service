package com.wpanther.storage.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtService Tests")
class JwtServiceTest {

    private JwtService jwtService;

    private static final String TEST_USERNAME = "service-test";
    private static final String TEST_SECRET = "404E635266556A586E3272354E39423F4428472B4B6250645367566B59703373367639792F423F4528482B4D6251655468576D5A7134743777397A24432646294A404E635266556A586E3272357538782F413F4428472B4B6250645367566B59703373367639792F425A452D4A614E645267556B58703273357538782F413F4428472B4B6250645367533879";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86400000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604800000L);
    }

    @Nested
    @DisplayName("generateToken() Tests")
    class GenerateTokenTests {

        @Test
        @DisplayName("Should generate valid JWT token")
        void shouldGenerateValidToken() {
            // When
            String token = jwtService.generateToken(TEST_USERNAME);

            // Then
            assertThat(token).isNotNull();
            assertThat(token).isNotEmpty();
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("Should generate token with correct subject")
        void shouldGenerateTokenWithCorrectSubject() {
            // When
            String token = jwtService.generateToken(TEST_USERNAME);

            // Then
            String extractedUsername = jwtService.extractUsername(token);
            assertThat(extractedUsername).isEqualTo(TEST_USERNAME);
        }

        @Test
        @DisplayName("Should generate token with expiration")
        void shouldGenerateTokenWithExpiration() {
            // When
            String token = jwtService.generateToken(TEST_USERNAME);

            // Then
            Date expiration = jwtService.extractClaim(token, Claims::getExpiration);
            assertThat(expiration).isAfter(new Date());
        }

        @Test
        @DisplayName("Should generate token with additional claims")
        void shouldGenerateTokenWithAdditionalClaims() {
            // Given
            Map<String, Object> extraClaims = Map.of("role", "ADMIN", "service", "document-storage");

            // When
            String token = jwtService.generateToken(extraClaims, TEST_USERNAME);

            // Then
            Object roleValue = jwtService.extractClaim(token, claims -> claims.get("role", String.class));
            assertThat(roleValue).isEqualTo("ADMIN");
        }
    }

    @Nested
    @DisplayName("extractUsername() Tests")
    class ExtractUsernameTests {

        @Test
        @DisplayName("Should extract username from valid token")
        void shouldExtractUsernameFromValidToken() {
            // Given
            String token = jwtService.generateToken(TEST_USERNAME);

            // When
            String username = jwtService.extractUsername(token);

            // Then
            assertThat(username).isEqualTo(TEST_USERNAME);
        }

        @Test
        @DisplayName("Should throw exception for invalid token")
        void shouldThrowForInvalidToken() {
            // Given
            String invalidToken = "invalid.token.here";

            // When & Then
            assertThatThrownBy(() -> jwtService.extractUsername(invalidToken))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("isTokenValid() Tests")
    class IsTokenValidTests {

        @Test
        @DisplayName("Should return true for valid token")
        void shouldReturnTrueForValidToken() {
            // Given
            String token = jwtService.generateToken(TEST_USERNAME);

            // When & Then
            assertThat(jwtService.isTokenValid(token, TEST_USERNAME)).isTrue();
        }

        @Test
        @DisplayName("Should return false for token with wrong username")
        void shouldReturnFalseForWrongUsername() {
            // Given
            String token = jwtService.generateToken(TEST_USERNAME);

            // When & Then
            assertThat(jwtService.isTokenValid(token, "different-user")).isFalse();
        }

        @Test
        @DisplayName("Should return false for invalid token")
        void shouldReturnFalseForInvalidToken() {
            // Given
            String invalidToken = "not-a-valid-jwt";

            // When & Then
            assertThat(jwtService.isTokenValid(invalidToken, TEST_USERNAME)).isFalse();
        }
    }

    @Nested
    @DisplayName("isTokenExpired() Tests")
    class IsTokenExpiredTests {

        @Test
        @DisplayName("Should return false for valid non-expired token")
        void shouldReturnFalseForNonExpiredToken() {
            // Given
            String token = jwtService.generateToken(TEST_USERNAME);

            // When & Then
            assertThat(jwtService.isTokenExpired(token)).isFalse();
        }

        @Test
        @DisplayName("Should return true for expired token")
        void shouldReturnTrueForExpiredToken() {
            // Given - create expired token
            SecretKey key = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(TEST_SECRET));
            String expiredToken = Jwts.builder()
                    .subject(TEST_USERNAME)
                    .issuedAt(new Date(System.currentTimeMillis() - 2000))
                    .expiration(new Date(System.currentTimeMillis() - 1000))
                    .signWith(key)
                    .compact();

            // When & Then
            assertThat(jwtService.isTokenExpired(expiredToken)).isTrue();
        }
    }

    @Nested
    @DisplayName("generateRefreshToken() Tests")
    class GenerateRefreshTokenTests {

        @Test
        @DisplayName("Should generate refresh token with longer expiration")
        void shouldGenerateRefreshTokenWithLongerExpiration() {
            // When
            String accessToken = jwtService.generateToken(TEST_USERNAME);
            String refreshToken = jwtService.generateRefreshToken(TEST_USERNAME);

            // Then
            Date accessExpiration = jwtService.extractClaim(accessToken, Claims::getExpiration);
            Date refreshExpiration = jwtService.extractClaim(refreshToken, Claims::getExpiration);

            assertThat(refreshExpiration).isAfter(accessExpiration);
        }

        @Test
        @DisplayName("Should have same subject for access and refresh tokens")
        void shouldHaveSameSubject() {
            // When
            String accessToken = jwtService.generateToken(TEST_USERNAME);
            String refreshToken = jwtService.generateRefreshToken(TEST_USERNAME);

            // Then
            assertThat(jwtService.extractUsername(accessToken))
                    .isEqualTo(jwtService.extractUsername(refreshToken));
        }
    }

    @Nested
    @DisplayName("extractClaim() Tests")
    class ExtractClaimTests {

        @Test
        @DisplayName("Should extract issuedAt claim")
        void shouldExtractIssuedAt() {
            // Given
            String token = jwtService.generateToken(TEST_USERNAME);

            // When
            Date issuedAt = jwtService.extractClaim(token, Claims::getIssuedAt);

            // Then
            assertThat(issuedAt).isNotNull();
            assertThat(issuedAt).isBefore(new Date());
        }

        @Test
        @DisplayName("Should extract custom claim")
        void shouldExtractCustomClaim() {
            // Given
            Map<String, Object> extraClaims = Map.of("customField", "customValue");
            String token = jwtService.generateToken(extraClaims, TEST_USERNAME);

            // When
            String customValue = jwtService.extractClaim(
                    token,
                    claims -> claims.get("customField", String.class)
            );

            // Then
            assertThat(customValue).isEqualTo("customValue");
        }
    }
}
