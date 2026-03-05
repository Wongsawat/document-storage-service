package com.wpanther.storage.infrastructure.adapter.inbound.security.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SecurityException Tests")
class SecurityExceptionTest {

    @Nested
    @DisplayName("InvalidTokenException")
    class InvalidTokenExceptionTests {

        @Test
        @DisplayName("Should create exception with message and token")
        void shouldCreateWithMessageAndToken() {
            String token = "invalid.jwt.token";
            InvalidTokenException ex = new InvalidTokenException("Invalid token", token);

            assertEquals("Invalid token", ex.getMessage());
            assertEquals("INVALID_TOKEN", ex.getCode());
            assertEquals(token, ex.getToken());
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("Should create exception with message, token, and cause")
        void shouldCreateWithMessageTokenAndCause() {
            String token = "malformed.jwt";
            Throwable cause = new RuntimeException("Parse error");
            InvalidTokenException ex = new InvalidTokenException("Malformed token", token, cause);

            assertEquals("Malformed token", ex.getMessage());
            assertEquals("INVALID_TOKEN", ex.getCode());
            assertEquals(token, ex.getToken());
            assertEquals(cause, ex.getCause());
        }

        @Test
        @DisplayName("Should be instance of SecurityException")
        void shouldBeInstanceOfSecurityException() {
            InvalidTokenException ex = new InvalidTokenException("Error", "token");

            assertTrue(ex instanceof SecurityException);
        }

        @Test
        @DisplayName("Should be instance of RuntimeException")
        void shouldBeInstanceOfRuntimeException() {
            InvalidTokenException ex = new InvalidTokenException("Error", "token");

            assertTrue(ex instanceof RuntimeException);
        }
    }

    @Nested
    @DisplayName("AuthenticationFailedException")
    class AuthenticationFailedExceptionTests {

        @Test
        @DisplayName("Should create exception with message")
        void shouldCreateWithMessage() {
            AuthenticationFailedException ex = new AuthenticationFailedException("Invalid credentials");

            assertEquals("Invalid credentials", ex.getMessage());
            assertEquals("AUTHENTICATION_FAILED", ex.getCode());
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("Should create exception with message and cause")
        void shouldCreateWithMessageAndCause() {
            Throwable cause = new RuntimeException("DB error");
            AuthenticationFailedException ex = new AuthenticationFailedException("Authentication failed", cause);

            assertEquals("Authentication failed", ex.getMessage());
            assertEquals("AUTHENTICATION_FAILED", ex.getCode());
            assertEquals(cause, ex.getCause());
        }

        @Test
        @DisplayName("Should be instance of SecurityException")
        void shouldBeInstanceOfSecurityException() {
            AuthenticationFailedException ex = new AuthenticationFailedException("Error");

            assertTrue(ex instanceof SecurityException);
        }

        @Test
        @DisplayName("Should be instance of RuntimeException")
        void shouldBeInstanceOfRuntimeException() {
            AuthenticationFailedException ex = new AuthenticationFailedException("Error");

            assertTrue(ex instanceof RuntimeException);
        }
    }

    @Nested
    @DisplayName("AuthorizationFailedException")
    class AuthorizationFailedExceptionTests {

        @Test
        @DisplayName("Should create exception with message and required role")
        void shouldCreateWithMessageAndRequiredRole() {
            AuthorizationFailedException ex = new AuthorizationFailedException(
                "Insufficient permissions", "ADMIN"
            );

            assertEquals("Insufficient permissions", ex.getMessage());
            assertEquals("AUTHORIZATION_FAILED", ex.getCode());
            assertEquals("ADMIN", ex.getRequiredRole());
            assertNull(ex.getRequiredPermission());
        }

        @Test
        @DisplayName("Should create exception with message and required permission")
        void shouldCreateWithMessageAndRequiredPermission() {
            AuthorizationFailedException ex = new AuthorizationFailedException(
                "Permission required", "document:write", true
            );

            assertEquals("Permission required", ex.getMessage());
            assertEquals("AUTHORIZATION_FAILED", ex.getCode());
            assertEquals("document:write", ex.getRequiredPermission());
            assertNull(ex.getRequiredRole());
        }

        @Test
        @DisplayName("Should be instance of SecurityException")
        void shouldBeInstanceOfSecurityException() {
            AuthorizationFailedException ex = new AuthorizationFailedException("Error", "ADMIN");

            assertTrue(ex instanceof SecurityException);
        }

        @Test
        @DisplayName("Should be instance of RuntimeException")
        void shouldBeInstanceOfRuntimeException() {
            AuthorizationFailedException ex = new AuthorizationFailedException("Error", "ADMIN");

            assertTrue(ex instanceof RuntimeException);
        }
    }

    @Nested
    @DisplayName("Common behavior")
    class CommonBehaviorTests {

        @Test
        @DisplayName("All exceptions should have non-null codes")
        void allExceptionsShouldHaveNonNullCodes() {
            InvalidTokenException invalidToken = new InvalidTokenException("Error", "token");
            AuthenticationFailedException authFailed = new AuthenticationFailedException("Error");
            AuthorizationFailedException authzFailed = new AuthorizationFailedException("Error", "ADMIN");

            assertNotNull(invalidToken.getCode());
            assertNotNull(authFailed.getCode());
            assertNotNull(authzFailed.getCode());
        }

        @Test
        @DisplayName("All exceptions should be throwable")
        void allExceptionsShouldBeThrowable() {
            InvalidTokenException ex1 = new InvalidTokenException("Error", "token");
            AuthenticationFailedException ex2 = new AuthenticationFailedException("Error");
            AuthorizationFailedException ex3 = new AuthorizationFailedException("Error", "ADMIN");

            assertThrows(InvalidTokenException.class, () -> { throw ex1; });
            assertThrows(AuthenticationFailedException.class, () -> { throw ex2; });
            assertThrows(AuthorizationFailedException.class, () -> { throw ex3; });
        }
    }
}
