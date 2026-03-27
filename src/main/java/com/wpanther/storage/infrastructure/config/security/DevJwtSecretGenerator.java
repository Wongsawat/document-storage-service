package com.wpanther.storage.infrastructure.config.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates a secure random JWT secret at startup in development mode.
 * <p>
 * This component activates only when the "dev" profile is active and the
 * JWT secret is set to the placeholder value "GENERATE_DEV_SECRET".
 * It replaces the placeholder with a cryptographically secure 512-bit (64-byte)
 * random secret to prevent accidental use of development secrets in production.
 * </p>
 * <p>
 * Usage: In application-dev.yml, set:
 * <pre>
 * app:
 *   security:
 *     jwt:
 *       secret: GENERATE_DEV_SECRET
 * </pre>
 * </p>
 */
@Component
@Profile("dev")
@Slf4j
public class DevJwtSecretGenerator {

    private static final String PLACEHOLDER = "GENERATE_DEV_SECRET";
    private static final int SECRET_BYTES = 64; // 512 bits for extra security margin
    private static final String PROPERTY_NAME = "app.security.jwt.secret";

    private final Environment environment;

    /**
     * Constructor for injection and immediate property override.
     *
     * @param environment Spring environment
     */
    public DevJwtSecretGenerator(Environment environment) {
        this.environment = environment;
        overrideSecretIfNeeded();
    }

    /**
     * Checks if the given secret value is the placeholder.
     *
     * @param secret the JWT secret to check
     * @return true if the secret is the placeholder value
     */
    boolean isPlaceholderValue(String secret) {
        return PLACEHOLDER.equals(secret);
    }

    /**
     * Generates a cryptographically secure random JWT secret.
     *
     * @return base64-encoded 512-bit random secret
     */
    String generateSecureSecret() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] secretBytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secretBytes);
        return Base64.getEncoder().encodeToString(secretBytes);
    }

    /**
     * Overrides the JWT secret if placeholder is detected and dev profile is active.
     * <p>
     * This method adds a new PropertySource with the generated secret to the
     * Spring Environment, ensuring @Value injections receive the generated value.
     * </p>
     */
    private void overrideSecretIfNeeded() {
        // Check if dev profile is actually active
        if (!isActiveProfile("dev")) {
            return;
        }

        // Check if current secret is placeholder
        String currentSecret = environment.getProperty(PROPERTY_NAME);
        if (!isPlaceholderValue(currentSecret)) {
            return;
        }

        // Generate secure random secret
        String generatedSecret = generateSecureSecret();

        // Add as new PropertySource with highest priority
        if (environment instanceof ConfigurableEnvironment configurableEnv) {
            Map<String, Object> overrides = new HashMap<>();
            overrides.put(PROPERTY_NAME, generatedSecret);

            PropertySource<Map<String, Object>> propertySource =
                new MapPropertySource("devJwtSecretOverride", overrides);

            configurableEnv.getPropertySources().addFirst(propertySource);
        }

        log.warn("======================================================================");
        log.warn("AUTO-GENERATED JWT SECRET FOR DEVELOPMENT MODE");
        log.warn("The placeholder 'GENERATE_DEV_SECRET' was replaced with a random secret.");
        log.warn("Generated secret (base64): {}", generatedSecret);
        log.warn("NEVER use this secret in production!");
        log.warn("======================================================================");
    }

    /**
     * Checks if the given profile is active.
     *
     * @param profile the profile name to check
     * @return true if the profile is active
     */
    private boolean isActiveProfile(String profile) {
        for (String activeProfile : environment.getActiveProfiles()) {
            if (activeProfile.equals(profile)) {
                return true;
            }
        }
        return false;
    }
}
