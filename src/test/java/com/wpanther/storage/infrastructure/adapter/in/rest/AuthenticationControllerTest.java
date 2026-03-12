package com.wpanther.storage.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.storage.domain.repository.DocumentRepositoryPort;
import com.wpanther.storage.infrastructure.adapter.in.security.JwtAccessDeniedHandler;
import com.wpanther.storage.infrastructure.adapter.in.security.JwtAuthenticationAdapter;
import com.wpanther.storage.infrastructure.adapter.in.security.JwtAuthenticationEntryPoint;
import com.wpanther.storage.infrastructure.adapter.in.security.JwtService;
import com.wpanther.storage.infrastructure.adapter.in.security.RateLimitingFilter;
import com.wpanther.storage.infrastructure.adapter.in.security.TokenBlacklistService;
import com.wpanther.storage.application.port.out.StorageProviderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthenticationController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "app.security.rate-limit.enabled=false"
})
@DisplayName("AuthenticationController Tests")
class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtAuthenticationAdapter jwtAuthenticationAdapter;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockBean
    private JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @MockBean
    private RateLimitingFilter rateLimitingFilter;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    // Mock beans needed to avoid loading issues
    @MockBean
    private DocumentRepositoryPort documentRepositoryPort;

    @MockBean
    private StorageProviderPort fileStorageProviderPort;

    private UserDetails testUser;

    @BeforeEach
    void setUp() {
        testUser = new User(
                "test-user",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class LoginTests {

        @Test
        @DisplayName("Should return JWT token on successful login")
        void shouldReturnTokenOnSuccessfulLogin() throws Exception {
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    testUser, null, testUser.getAuthorities()
            );

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(userDetailsService.loadUserByUsername("test-user")).thenReturn(testUser);
            when(jwtService.generateToken("test-user")).thenReturn("jwt-token-123");
            when(jwtService.generateRefreshToken("test-user")).thenReturn("refresh-token-456");
            when(jwtService.getJwtExpiration()).thenReturn(3600000L);

            String requestBody = """
                    {
                        "username": "test-user",
                        "password": "password"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt-token-123"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token-456"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(3600))
                    .andExpect(jsonPath("$.username").value("test-user"))
                    .andExpect(jsonPath("$.authorities").isArray())
                    .andExpect(jsonPath("$.authorities[0]").value("ROLE_USER"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/token")
    class TokenTests {

        @Test
        @DisplayName("Should return token map on successful authentication")
        void shouldReturnTokenMapOnSuccess() throws Exception {
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    testUser, null, testUser.getAuthorities()
            );

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(userDetailsService.loadUserByUsername("test-user")).thenReturn(testUser);
            when(jwtService.generateToken("test-user")).thenReturn("jwt-token-123");
            when(jwtService.getJwtExpiration()).thenReturn(3600000L);

            String requestBody = """
                    {
                        "username": "test-user",
                        "password": "password"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt-token-123"))
                    .andExpect(jsonPath("$.type").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(3600))
                    .andExpect(jsonPath("$.username").value("test-user"))
                    .andExpect(jsonPath("$.authorities").isArray());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should return new tokens on valid refresh token")
        void shouldReturnNewTokensOnValidRefresh() throws Exception {
            when(jwtService.extractUsername("valid-refresh-token")).thenReturn("test-user");
            when(jwtService.isTokenValid(eq("valid-refresh-token"), eq("test-user"))).thenReturn(true);
            when(userDetailsService.loadUserByUsername("test-user")).thenReturn(testUser);
            when(jwtService.generateToken("test-user")).thenReturn("new-jwt-token");
            when(jwtService.generateRefreshToken("test-user")).thenReturn("new-refresh-token");
            when(jwtService.getJwtExpiration()).thenReturn(3600000L);

            String requestBody = """
                    {
                        "refreshToken": "valid-refresh-token"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("new-jwt-token"))
                    .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
                    .andExpect(jsonPath("$.username").value("test-user"));
        }

        @Test
        @DisplayName("Should return 400 on blank refresh token")
        void shouldReturn400OnBlankRefreshToken() throws Exception {
            String requestBody = """
                    {
                        "refreshToken": ""
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 on null refresh token")
        void shouldReturn400OnNullRefreshToken() throws Exception {
            String requestBody = """
                    {
                        "refreshToken": null
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/validate")
    class ValidateTokenTests {

        @Test
        @DisplayName("Should return user info for valid token")
        void shouldReturnUserInfoForValidToken() throws Exception {
            String token = "valid-jwt-token";

            when(jwtService.extractUsername(token)).thenReturn("test-user");
            when(jwtService.isTokenValid(eq(token), eq("test-user"))).thenReturn(true);
            when(userDetailsService.loadUserByUsername("test-user")).thenReturn(testUser);
            when(jwtService.extractClaim(eq(token), any())).thenReturn(java.time.Instant.now());

            mockMvc.perform(get("/api/v1/auth/validate")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.username").value("test-user"))
                    .andExpect(jsonPath("$.authorities").isNotEmpty());
        }

        @Test
        @DisplayName("Should return 400 for missing authorization header")
        void shouldReturn400ForMissingAuthHeader() throws Exception {
            mockMvc.perform(get("/api/v1/auth/validate"))
                    .andExpect(status().isBadRequest());
        }
        @Test
        @DisplayName("Should return 401 for invalid authorization header format")
        void shouldReturn401ForInvalidAuthHeaderFormat() throws Exception {
            mockMvc.perform(get("/api/v1/auth/validate")
                            .header("Authorization", "InvalidFormat token"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 401 for expired token")
        void shouldReturn401ForExpiredToken() throws Exception {
            String token = "expired-token";

            when(jwtService.extractUsername(token)).thenReturn("test-user");
            when(jwtService.isTokenValid(eq(token), eq("test-user"))).thenReturn(false);

            mockMvc.perform(get("/api/v1/auth/validate")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("LoginRequest Record")
    class LoginRequestRecordTests {

        @Test
        @DisplayName("Should create LoginRequest with username and password")
        void shouldCreateLoginRequest() {
            AuthenticationController.LoginRequest request = new AuthenticationController.LoginRequest(
                    "test-user", "password123"
            );

            assertEquals("test-user", request.username());
            assertEquals("password123", request.password());
        }

        @Test
        @DisplayName("Should accept null values in LoginRequest")
        void shouldAcceptNullValues() {
            AuthenticationController.LoginRequest request = new AuthenticationController.LoginRequest(
                    null, null
            );

            assertEquals(null, request.username());
            assertEquals(null, request.password());
        }
    }

    @Nested
    @DisplayName("RefreshTokenRequest Record")
    class RefreshTokenRequestRecordTests {

        @Test
        @DisplayName("Should create RefreshTokenRequest with refresh token")
        void shouldCreateRefreshTokenRequest() {
            AuthenticationController.RefreshTokenRequest request =
                    new AuthenticationController.RefreshTokenRequest("refresh-token-123");

            assertEquals("refresh-token-123", request.refreshToken());
        }

        @Test
        @DisplayName("Should accept null in RefreshTokenRequest")
        void shouldAcceptNull() {
            AuthenticationController.RefreshTokenRequest request =
                    new AuthenticationController.RefreshTokenRequest(null);

            assertEquals(null, request.refreshToken());
        }
    }

    @Nested
    @DisplayName("AuthResponse Record")
    class AuthResponseRecordTests {

        @Test
        @DisplayName("Should create AuthResponse with all fields")
        void shouldCreateAuthResponse() {
            AuthenticationController.AuthResponse response = new AuthenticationController.AuthResponse(
                    "jwt-token", "refresh-token", "Bearer", 3600L, "user", List.of("ROLE_USER")
            );

            assertEquals("jwt-token", response.token());
            assertEquals("refresh-token", response.refreshToken());
            assertEquals("Bearer", response.tokenType());
            assertEquals(3600L, response.expiresIn());
            assertEquals("user", response.username());
            assertEquals(List.of("ROLE_USER"), response.authorities());
        }
    }

    @Nested
    @DisplayName("AuthResponse DTO")
    class AuthResponseTests {

        @Test
        @DisplayName("Should contain all required fields in response")
        void shouldContainAllRequiredFields() throws Exception {
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    testUser, null, testUser.getAuthorities()
            );

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(userDetailsService.loadUserByUsername("test-user")).thenReturn(testUser);
            when(jwtService.generateToken("test-user")).thenReturn("jwt-token");
            when(jwtService.generateRefreshToken("test-user")).thenReturn("refresh-token");
            when(jwtService.getJwtExpiration()).thenReturn(3600000L);

            String requestBody = """
                    {
                        "username": "test-user",
                        "password": "password"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").exists())
                    .andExpect(jsonPath("$.refreshToken").exists())
                    .andExpect(jsonPath("$.tokenType").exists())
                    .andExpect(jsonPath("$.expiresIn").exists())
                    .andExpect(jsonPath("$.username").exists())
                    .andExpect(jsonPath("$.authorities").exists());
        }
    }
}
