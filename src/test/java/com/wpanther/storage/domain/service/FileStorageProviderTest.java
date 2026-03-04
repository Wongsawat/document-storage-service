package com.wpanther.storage.domain.service;

import com.wpanther.storage.domain.service.FileStorageProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FileStorageProvider Interface Tests")
class FileStorageProviderTest {

    @Nested
    @DisplayName("StorageResult Record Tests")
    class StorageResultTests {

        @Test
        @DisplayName("Should create StorageResult with path and URL")
        void shouldCreateStorageResult() {
            // Given
            String path = "/path/to/file.pdf";
            String url = "http://example.com/file.pdf";

            // When
            FileStorageProvider.StorageResult result = new FileStorageProvider.StorageResult(path, url);

            // Then
            assertThat(result.path()).isEqualTo(path);
            assertThat(result.url()).isEqualTo(url);
        }

        @Test
        @DisplayName("Should support record pattern matching")
        void shouldSupportPatternMatching() {
            // Given
            FileStorageProvider.StorageResult result = new FileStorageProvider.StorageResult("/path", "http://url");

            // When
            String extracted = switch (result) {
                case FileStorageProvider.StorageResult(String p, String u) -> "Path: " + p + ", URL: " + u;
            };

            // Then
            assertThat(extracted).isEqualTo("Path: /path, URL: http://url");
        }
    }

    @Nested
    @DisplayName("StorageException Tests")
    class StorageExceptionTests {

        @Test
        @DisplayName("Should create exception with message")
        void shouldCreateExceptionWithMessage() {
            // Given
            String message = "Storage failed";

            // When
            FileStorageProvider.StorageException exception = new FileStorageProvider.StorageException(message);

            // Then
            assertThat(exception).hasMessage(message);
            assertThat(exception.getCause()).isNull();
        }

        @Test
        @DisplayName("Should create exception with message and cause")
        void shouldCreateExceptionWithMessageAndCause() {
            // Given
            String message = "Storage failed";
            Throwable cause = new RuntimeException("Disk full");

            // When
            FileStorageProvider.StorageException exception =
                    new FileStorageProvider.StorageException(message, cause);

            // Then
            assertThat(exception).hasMessage(message);
            assertThat(exception.getCause()).isEqualTo(cause);
        }

        @Test
        @DisplayName("Should support catching as generic exception")
        void shouldSupportCatchingAsGenericException() {
            // Given
            FileStorageProvider.StorageException storageException =
                    new FileStorageProvider.StorageException("Test error");

            // When & Then
            Exception exception = storageException;
            assertThat(exception).isInstanceOf(FileStorageProvider.StorageException.class);
        }
    }
}
