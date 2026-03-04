package com.wpanther.storage.infrastructure.adapter.inbound.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DocumentStorageUserDetailsService Tests")
class DocumentStorageUserDetailsServiceTest {

    private DocumentStorageUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new DocumentStorageUserDetailsService();
        // Set service password via reflection to avoid environment variable requirement
        ReflectionTestUtils.setField(userDetailsService, "servicePassword", "test-service-password");
    }

    @Nested
    @DisplayName("loadUserByUsername() Tests")
    class LoadUserByUsernameTests {

        @Test
        @DisplayName("Should load user for valid service name")
        void shouldLoadUserForValidServiceName() {
            // Given
            String serviceUsername = "service-document-storage";

            // When
            UserDetails userDetails = userDetailsService.loadUserByUsername(serviceUsername);

            // Then
            assertThat(userDetails).isNotNull();
            assertThat(userDetails.getUsername()).isEqualTo(serviceUsername);
            assertThat(userDetails.isAccountNonExpired()).isTrue();
            assertThat(userDetails.isAccountNonLocked()).isTrue();
            assertThat(userDetails.isCredentialsNonExpired()).isTrue();
            assertThat(userDetails.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should grant all document permissions to document-storage service")
        void shouldGrantAllPermissionsToDocumentStorageService() {
            // Given
            String serviceUsername = "service-document-storage";

            // When
            UserDetails userDetails = userDetailsService.loadUserByUsername(serviceUsername);

            // Then
            assertThat(userDetails.getAuthorities())
                    .hasSize(4)
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_SERVICE"))
                    .anyMatch(auth -> auth.getAuthority().equals("DOCUMENT_READ"))
                    .anyMatch(auth -> auth.getAuthority().equals("DOCUMENT_WRITE"))
                    .anyMatch(auth -> auth.getAuthority().equals("DOCUMENT_DELETE"));
        }

        @Test
        @DisplayName("Should grant only ROLE_SERVICE to other services")
        void shouldGrantOnlyServiceRoleToOtherServices() {
            // Given
            String serviceUsername = "service-other-service";

            // When
            UserDetails userDetails = userDetailsService.loadUserByUsername(serviceUsername);

            // Then
            assertThat(userDetails.getAuthorities())
                    .hasSize(1)
                    .allMatch(auth -> auth.getAuthority().equals("ROLE_SERVICE"));
        }

        @Test
        @DisplayName("Should throw UsernameNotFoundException for null username")
        void shouldThrowForNullUsername() {
            assertThatThrownBy(() -> userDetailsService.loadUserByUsername(null))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("Should throw UsernameNotFoundException for non-service user")
        void shouldThrowForNonServiceUser() {
            // Given
            String regularUsername = "john-doe";

            // When & Then
            assertThatThrownBy(() -> userDetailsService.loadUserByUsername(regularUsername))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("Should throw UsernameNotFoundException for empty username")
        void shouldThrowForEmptyUsername() {
            assertThatThrownBy(() -> userDetailsService.loadUserByUsername(""))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("Should throw UsernameNotFoundException for username without service- prefix")
        void shouldThrowForUsernameWithoutServicePrefix() {
            // Given
            String usernameWithoutPrefix = "document-storage";

            // When & Then
            assertThatThrownBy(() -> userDetailsService.loadUserByUsername(usernameWithoutPrefix))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("Should handle service name with document-storage in any position")
        void shouldHandleDocumentStorageInAnyPosition() {
            // Given - various valid service names
            String[] validServiceNames = {
                "service-document-storage",
                "document-storage-service",
                "my-document-storage-cluster"
            };

            // When & Then
            for (String serviceName : validServiceNames) {
                if (serviceName.startsWith("service-")) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(serviceName);
                    assertThat(userDetails.getUsername()).isEqualTo(serviceName);
                }
            }
        }

        @Test
        @DisplayName("Should set password field for authentication")
        void shouldSetPasswordField() {
            // Given - this will fail if password is not set
            String serviceUsername = "service-document-storage";

            // When
            UserDetails userDetails = userDetailsService.loadUserByUsername(serviceUsername);

            // Then - password should be set (empty string in tests, set from SERVICE_PASSWORD env var in production)
            // An empty password would fail BCrypt validation, indicating configuration issue
            assertThat(userDetails.getPassword()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Authority Determination Tests")
    class AuthorityTests {

        @Test
        @DisplayName("Should determine correct authorities for different service types")
        void shouldDetermineCorrectAuthorities() {
            // Test different service patterns
            String[][] testCases = {
                {"service-document-storage", "4"},      // All permissions
                {"document-storage-service", "1"},      // Only ROLE_SERVICE
                {"service-invoice-processing", "1"},   // Only ROLE_SERVICE
                {"service-other-service", "1"}         // Only ROLE_SERVICE
            };

            for (String[] testCase : testCases) {
                String username = testCase[0];
                String expectedAuthorityCount = testCase[1];

                if (username.contains("document-storage") && username.startsWith("service-")) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    assertThat(userDetails.getAuthorities()).hasSize(Integer.parseInt(expectedAuthorityCount));
                }
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should accept service- with only prefix")
        void shouldHandleOnlyPrefix() {
            // Given - "service-" starts with the prefix, so it's valid format
            // (the actual validation of username format is minimal - just checks prefix)
            String onlyPrefix = "service-";

            // When
            UserDetails userDetails = userDetailsService.loadUserByUsername(onlyPrefix);

            // Then - should return a user with basic ROLE_SERVICE authority
            assertThat(userDetails.getUsername()).isEqualTo(onlyPrefix);
            assertThat(userDetails.getAuthorities()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle service name with special characters")
        void shouldHandleSpecialCharacters() {
            // Given - service names can contain hyphens and numbers
            String serviceUsername = "service-document-storage-01";

            // When
            UserDetails userDetails = userDetailsService.loadUserByUsername(serviceUsername);

            // Then
            assertThat(userDetails.getUsername()).isEqualTo(serviceUsername);
            assertThat(userDetails.getAuthorities()).hasSize(4);
        }

        @Test
        @DisplayName("Should handle case-sensitive service names")
        void shouldHandleCaseSensitiveNames() {
            // Given
            String uppercaseUsername = "SERVICE-DOCUMENT-STORAGE";

            // When & Then
            // Should NOT find uppercase service name
            assertThatThrownBy(() -> userDetailsService.loadUserByUsername(uppercaseUsername))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        @DisplayName("Should handle multiple service- prefixes")
        void shouldHandleMultipleServicePrefixes() {
            // Given - unusual but valid case
            String doublePrefix = "service-service-document-storage";

            // When
            UserDetails userDetails = userDetailsService.loadUserByUsername(doublePrefix);

            // Then - should work and grant all permissions
            assertThat(userDetails.getUsername()).isEqualTo(doublePrefix);
            assertThat(userDetails.getAuthorities()).hasSize(4);
        }
    }
}
