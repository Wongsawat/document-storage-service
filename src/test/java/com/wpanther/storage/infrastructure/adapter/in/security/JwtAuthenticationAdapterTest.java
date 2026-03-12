package com.wpanther.storage.infrastructure.adapter.in.security;

import com.wpanther.storage.infrastructure.adapter.inbound.security.exception.SecurityException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtAuthenticationAdapter Tests")
class JwtAuthenticationAdapterTest {

    private JwtAuthenticationAdapter jwtAuthFilter;
    private JwtService jwtService;
    private UserDetailsService userDetailsService;
    private FilterChain filterChain;
    private TokenBlacklistService tokenBlacklistService;

    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(
            "test-secret-key-for-testing-purposes-only-32bytes".getBytes(StandardCharsets.UTF_8)
    );

    private static final String TEST_USERNAME = "service-document-storage";

    @BeforeEach
    void setUp() {
        tokenBlacklistService = mock(TokenBlacklistService.class);
        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);

        jwtService = new JwtService(TEST_SECRET, 86400000L, 604800000L, tokenBlacklistService);
        userDetailsService = mock(UserDetailsService.class);
        filterChain = mock(FilterChain.class);

        jwtAuthFilter = new JwtAuthenticationAdapter(jwtService, userDetailsService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("doFilterInternal() Tests")
    class DoFilterInternalTests {

        @Test
        @DisplayName("Should set authentication when valid JWT provided")
        void shouldSetAuthenticationWhenValidJwt() throws Exception {
            // Given
            String token = jwtService.generateToken(TEST_USERNAME);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + token);
            MockHttpServletResponse response = new MockHttpServletResponse();

            UserDetails userDetails = User.builder()
                    .username(TEST_USERNAME)
                    .password("password")
                    .authorities("ROLE_SERVICE", "DOCUMENT_READ")
                    .build();
            when(userDetailsService.loadUserByUsername(TEST_USERNAME)).thenReturn(userDetails);

            // When
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Then
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNotNull();
            assertThat(authentication.getName()).isEqualTo(TEST_USERNAME);
            assertThat(authentication.getAuthorities())
                    .hasSize(2)
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_SERVICE"))
                    .anyMatch(auth -> auth.getAuthority().equals("DOCUMENT_READ"));

            verify(userDetailsService).loadUserByUsername(TEST_USERNAME);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should continue filter chain when no Authorization header")
        void shouldContinueWhenNoAuthorizationHeader() throws Exception {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            // When
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(userDetailsService, never()).loadUserByUsername(anyString());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should continue filter chain when Authorization header is not Bearer")
        void shouldContinueWhenAuthorizationNotBearer() throws Exception {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Basic dXNlcjpwYXNzd29yZA==");
            MockHttpServletResponse response = new MockHttpServletResponse();

            // When
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(userDetailsService, never()).loadUserByUsername(anyString());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should continue filter chain when token is invalid")
        void shouldContinueWhenTokenInvalid() throws Exception {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer invalid-token-here");
            MockHttpServletResponse response = new MockHttpServletResponse();

            // When
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(userDetailsService, never()).loadUserByUsername(anyString());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should continue filter chain when token is expired")
        void shouldContinueWhenTokenExpired() throws Exception {
            // Given - create expired token
            SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET));
            String expiredToken = Jwts.builder()
                    .subject(TEST_USERNAME)
                    .issuedAt(new Date(System.currentTimeMillis() - 2000))
                    .expiration(new Date(System.currentTimeMillis() - 1000))
                    .signWith(key)
                    .compact();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + expiredToken);
            MockHttpServletResponse response = new MockHttpServletResponse();

            // When
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(userDetailsService, never()).loadUserByUsername(anyString());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should not reload user details when authentication already set")
        void shouldNotReloadUserWhenAuthenticationSet() throws Exception {
            // Given
            String token = jwtService.generateToken(TEST_USERNAME);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + token);
            MockHttpServletResponse response = new MockHttpServletResponse();

            // Pre-set authentication
            Authentication existingAuth = mock(Authentication.class);
            SecurityContextHolder.getContext().setAuthentication(existingAuth);

            // When
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Then
            verify(userDetailsService, never()).loadUserByUsername(anyString());
            verify(filterChain).doFilter(request, response);

            // Cleanup
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("Should clear context when userDetailsService throws exception")
        void shouldClearContextWhenUserDetailsServiceThrows() throws Exception {
            // Given
            String token = jwtService.generateToken(TEST_USERNAME);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + token);
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(userDetailsService.loadUserByUsername(TEST_USERNAME))
                    .thenThrow(new UsernameNotFoundException("User not found"));

            // When
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should handle empty Authorization header")
        void shouldHandleEmptyAuthorizationHeader() throws Exception {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "");
            MockHttpServletResponse response = new MockHttpServletResponse();

            // When
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should handle Bearer token without actual token")
        void shouldHandleBearerWithoutToken() throws Exception {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer ");
            MockHttpServletResponse response = new MockHttpServletResponse();

            // When
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should set authentication details with request information")
        void shouldSetAuthenticationDetails() throws Exception {
            // Given
            String token = jwtService.generateToken(TEST_USERNAME);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.168.1.100");
            request.addHeader("Authorization", "Bearer " + token);
            MockHttpServletResponse response = new MockHttpServletResponse();

            UserDetails userDetails = User.builder()
                    .username(TEST_USERNAME)
                    .password("password")
                    .authorities("ROLE_SERVICE")
                    .build();
            when(userDetailsService.loadUserByUsername(TEST_USERNAME)).thenReturn(userDetails);

            // When
            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            // Then
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNotNull();
            assertThat(authentication.getDetails()).isNotNull();

            // Cleanup
            SecurityContextHolder.clearContext();
        }
    }

    @Nested
    @DisplayName("extractJwtFromRequest() Tests")
    class ExtractJwtTests {

        @Test
        @DisplayName("Should extract valid Bearer token")
        void shouldExtractBearerToken() throws Exception {
            // Given
            String token = jwtService.generateToken(TEST_USERNAME);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + token);

            UserDetails userDetails = User.builder()
                    .username(TEST_USERNAME)
                    .password("password")
                    .authorities("ROLE_SERVICE")
                    .build();
            when(userDetailsService.loadUserByUsername(TEST_USERNAME)).thenReturn(userDetails);

            // When
            jwtAuthFilter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

            // Then - if we got here without exception, extraction worked
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();

            // Cleanup
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("Should handle Bearer token with extra spaces")
        void shouldHandleBearerWithExtraSpaces() throws Exception {
            // Given - token with leading spaces after "Bearer " will be extracted with spaces
            // JWT validation will fail due to leading spaces in the token
            String token = jwtService.generateToken(TEST_USERNAME);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer  " + token);

            // When - token with leading spaces will be extracted but will fail JWT validation
            jwtAuthFilter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

            // Then - authentication should not be set because JWT parsing fails
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("Should return null when header is missing")
        void shouldReturnNullWhenHeaderMissing() throws Exception {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            // No Authorization header

            // When
            jwtAuthFilter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

            // Then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}
