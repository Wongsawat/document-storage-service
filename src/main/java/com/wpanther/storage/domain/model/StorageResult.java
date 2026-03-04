package com.wpanther.storage.domain.model;

import java.time.Instant;

/**
 * Value object representing the result of a storage operation.
 */
public record StorageResult(
    String location,
    String provider,
    Instant timestamp
) {
    public StorageResult {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location must not be null or blank");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be null or blank");
        }
    }

    public static StorageResult success(String location, String provider) {
        return new StorageResult(location, provider, Instant.now());
    }
}
