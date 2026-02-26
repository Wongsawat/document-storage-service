package com.wpanther.storage.infrastructure.security.config;

import com.wpanther.storage.infrastructure.security.JwtAccessDeniedHandler;
import com.wpanther.storage.infrastructure.security.JwtAuthenticationEntryPoint;
import com.wpanther.storage.infrastructure.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 6 configuration for JWT-based authentication.
 * <p>
 * Features:
 * - Stateless session management for JWT
 * - Role-based access control (RBAC)
 * - Method-level security with @PreAuthorize
 * - Public actuator endpoints for health checks
 * - Protected document storage endpoints
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    private final UserDetailsService userDetailsService;

    @Value("${app.security.cors.allowed-origins:http://localhost:3000,http://localhost:8080,http://localhost:8084}")
    private String corsAllowedOrigins;

    /**
     * Configure security filter chain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF for stateless JWT authentication
                .csrf(AbstractHttpConfigurer::disable)

                // Configure CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Configure authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public actuator endpoints
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()

                        // Public API for health check
                        .requestMatchers("/api/v1/health").permitAll()

                        // Public authentication endpoints (for JWT token exchange)
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // Document upload requires DOCUMENT_WRITE permission
                        .requestMatchers(HttpMethod.POST, "/api/v1/documents").hasAuthority("DOCUMENT_WRITE")

                        // Document download requires DOCUMENT_READ permission
                        .requestMatchers(HttpMethod.GET, "/api/v1/documents/**").hasAnyAuthority("DOCUMENT_READ", "DOCUMENT_WRITE")

                        // Document deletion requires DOCUMENT_DELETE permission
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/documents/**").hasAuthority("DOCUMENT_DELETE")

                        // All other requests require authentication
                        .anyRequest().authenticated()
                )

                // Configure session management - stateless for JWT
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Configure exception handling
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )

                // Add JWT filter before UsernamePasswordAuthenticationFilter
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration source
     */
    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();

        // Configure allowed origins from application properties
        configuration.setAllowedOrigins(java.util.List.of(corsAllowedOrigins.split(",")));

        // Configure allowed methods
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Configure allowed headers
        configuration.setAllowedHeaders(java.util.List.of("*"));

        // Allow credentials
        configuration.setAllowCredentials(true);

        // Expose headers for CORS
        configuration.setExposedHeaders(java.util.List.of("Authorization", "Content-Type"));

        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    /**
     * Authentication provider for user authentication
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Password encoder (BCrypt with strength 12)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Authentication manager for manual authentication
     */
    @Bean
    public org.springframework.security.authentication.AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * JWT properties for configuration and validation
     */
    @Bean
    public JwtConfigValidator.JwtProperties jwtProperties(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.expiration:86400000}") long jwtExpiration,
            @Value("${app.security.jwt.refresh-expiration:604800000}") long jwtRefreshExpiration
    ) {
        return new JwtConfigValidator.JwtProperties(secret, jwtExpiration, jwtRefreshExpiration);
    }
}
