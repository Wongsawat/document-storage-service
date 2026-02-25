package com.wpanther.storage.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * UserDetailsService implementation for JWT authentication.
 * Loads user details for service-to-service authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentStorageUserDetailsService implements UserDetailsService {

    private static final String SERVICE_USER_PREFIX = "service-";

    /**
     * Load user by username (service name).
     * In production, this would load from a database or external identity provider.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user details for: {}", username);

        // For service-to-service communication, validate the service name format
        if (username == null || !username.startsWith(SERVICE_USER_PREFIX)) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        // Extract roles based on service type
        List<SimpleGrantedAuthority> authorities = determineAuthorities(username);

        // Return Spring Security User object
        return User.builder()
                .username(username)
                .password("") // No password for JWT-based service authentication
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }

    /**
     * Determine authorities based on service name.
     * In production, this would be loaded from a database or configuration.
     */
    private List<SimpleGrantedAuthority> determineAuthorities(String username) {
        // Default to READ_WRITE role for document operations
        if (username.contains("document-storage")) {
            return List.of(
                    new SimpleGrantedAuthority("ROLE_SERVICE"),
                    new SimpleGrantedAuthority("DOCUMENT_READ"),
                    new SimpleGrantedAuthority("DOCUMENT_WRITE"),
                    new SimpleGrantedAuthority("DOCUMENT_DELETE")
            );
        }

        // Default service role with basic permissions
        return List.of(new SimpleGrantedAuthority("ROLE_SERVICE"));
    }
}
