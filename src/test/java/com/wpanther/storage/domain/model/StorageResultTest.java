package com.wpanther.storage.domain.model;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StorageResultTest {

    @Test
    void storageResult_createsSuccessfully() {
        StorageResult result = new StorageResult("/path/to/doc", "local", Instant.now());

        assertThat(result.location()).isEqualTo("/path/to/doc");
        assertThat(result.provider()).isEqualTo("local");
        assertThat(result.timestamp()).isNotNull();
    }

    @Test
    void successFactoryMethod_createsResultWithTimestamp() {
        StorageResult result = StorageResult.success("/path/to/doc", "local");

        assertThat(result.location()).isEqualTo("/path/to/doc");
        assertThat(result.provider()).isEqualTo("local");
        assertThat(result.timestamp()).isNotNull();
        assertThat(result.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void successFactoryMethod_nullLocation_throwsException() {
        assertThatThrownBy(() -> StorageResult.success(null, "local"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("location");
    }

    @Test
    void successFactoryMethod_blankLocation_throwsException() {
        assertThatThrownBy(() -> StorageResult.success("  ", "local"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("location");
    }

    @Test
    void successFactoryMethod_nullProvider_throwsException() {
        assertThatThrownBy(() -> StorageResult.success("/path", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("provider");
    }

    @Test
    void successFactoryMethod_blankProvider_throwsException() {
        assertThatThrownBy(() -> StorageResult.success("/path", "  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("provider");
    }
}
