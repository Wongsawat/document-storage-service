package com.wpanther.storage.infrastructure.adapter.inbound.security.config;

import com.wpanther.storage.infrastructure.adapter.inbound.security.JwtAccessDeniedHandler;
import com.wpanther.storage.infrastructure.adapter.inbound.security.JwtAuthenticationAdapter;
import com.wpanther.storage.infrastructure.adapter.inbound.security.JwtAuthenticationEntryPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SecurityConfig Tests")
class SecurityConfigTest {

    @Nested
    @DisplayName("Class annotations")
    class AnnotationTests {

        @Test
        @DisplayName("Should have Configuration annotation")
        void shouldHaveConfigurationAnnotation() {
            assertNotNull(SecurityConfig.class.getAnnotation(Configuration.class));
        }

        @Test
        @DisplayName("Should have EnableWebSecurity annotation")
        void shouldHaveEnableWebSecurityAnnotation() {
            EnableWebSecurity annotation = SecurityConfig.class.getAnnotation(EnableWebSecurity.class);
            assertNotNull(annotation);
        }

        @Test
        @DisplayName("Should have EnableMethodSecurity annotation")
        void shouldHaveEnableMethodSecurityAnnotation() {
            EnableMethodSecurity annotation = SecurityConfig.class.getAnnotation(EnableMethodSecurity.class);
            assertNotNull(annotation);
            assertTrue(annotation.securedEnabled());
            assertTrue(annotation.jsr250Enabled());
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should require constructor dependencies")
        void shouldRequireDependencies() throws Exception {
            var constructor = SecurityConfig.class.getDeclaredConstructors()[0];
            var parameters = constructor.getParameters();

            assertEquals(4, parameters.length);
        }

        @Test
        @DisplayName("Should have JwtAuthenticationAdapter parameter")
        void shouldHaveJwtAuthFilterParameter() throws Exception {
            var constructor = SecurityConfig.class.getDeclaredConstructors()[0];
            var parameters = constructor.getParameters();

            assertEquals(JwtAuthenticationAdapter.class, parameters[0].getType());
        }

        @Test
        @DisplayName("Should have JwtAuthenticationEntryPoint parameter")
        void shouldHaveAuthEntryPointParameter() throws Exception {
            var constructor = SecurityConfig.class.getDeclaredConstructors()[0];
            var parameters = constructor.getParameters();

            assertEquals(JwtAuthenticationEntryPoint.class, parameters[1].getType());
        }

        @Test
        @DisplayName("Should have JwtAccessDeniedHandler parameter")
        void shouldHaveAccessDeniedHandlerParameter() throws Exception {
            var constructor = SecurityConfig.class.getDeclaredConstructors()[0];
            var parameters = constructor.getParameters();

            assertEquals(JwtAccessDeniedHandler.class, parameters[2].getType());
        }

        @Test
        @DisplayName("Should have UserDetailsService parameter")
        void shouldHaveUserDetailsServiceParameter() throws Exception {
            var constructor = SecurityConfig.class.getDeclaredConstructors()[0];
            var parameters = constructor.getParameters();

            assertEquals(org.springframework.security.core.userdetails.UserDetailsService.class, parameters[3].getType());
        }
    }

    @Nested
    @DisplayName("Bean methods")
    class BeanMethodTests {

        @Test
        @DisplayName("Should have securityFilterChain bean method")
        void shouldHaveSecurityFilterChainMethod() throws Exception {
            var method = SecurityConfig.class.getDeclaredMethod("securityFilterChain", org.springframework.security.config.annotation.web.builders.HttpSecurity.class);
            assertNotNull(method.getAnnotation(org.springframework.context.annotation.Bean.class));
        }

        @Test
        @DisplayName("Should have corsConfigurationSource bean method")
        void shouldHaveCorsConfigurationSourceMethod() throws Exception {
            var method = SecurityConfig.class.getDeclaredMethod("corsConfigurationSource");
            assertNotNull(method.getAnnotation(org.springframework.context.annotation.Bean.class));
        }

        @Test
        @DisplayName("Should have authenticationProvider bean method")
        void shouldHaveAuthenticationProviderMethod() throws Exception {
            var method = SecurityConfig.class.getDeclaredMethod("authenticationProvider");
            assertNotNull(method.getAnnotation(org.springframework.context.annotation.Bean.class));
        }

        @Test
        @DisplayName("Should have passwordEncoder bean method")
        void shouldHavePasswordEncoderMethod() throws Exception {
            var method = SecurityConfig.class.getDeclaredMethod("passwordEncoder");
            assertNotNull(method.getAnnotation(org.springframework.context.annotation.Bean.class));
        }

        @Test
        @DisplayName("Should have authenticationManager bean method")
        void shouldHaveAuthenticationManagerMethod() throws Exception {
            var method = SecurityConfig.class.getDeclaredMethod("authenticationManager", org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration.class);
            assertNotNull(method.getAnnotation(org.springframework.context.annotation.Bean.class));
        }

        @Test
        @DisplayName("Should have jwtProperties bean method")
        void shouldHaveJwtPropertiesMethod() throws Exception {
            var method = SecurityConfig.class.getDeclaredMethod("jwtProperties", String.class, long.class, long.class);
            assertNotNull(method.getAnnotation(org.springframework.context.annotation.Bean.class));
        }
    }
}
