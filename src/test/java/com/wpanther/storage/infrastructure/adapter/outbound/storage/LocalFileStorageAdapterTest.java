package com.wpanther.storage.infrastructure.adapter.outbound.storage;

import com.wpanther.storage.domain.service.FileStorageProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

@DisplayName("LocalFileStorageAdapter Tests")
class LocalFileStorageAdapterTest {

    private LocalFileStorageAdapter storageProvider;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        storageProvider = new LocalFileStorageAdapter();
        // Use reflection to set basePath to temp directory
        try {
            java.lang.reflect.Field basePathField = LocalFileStorageAdapter.class.getDeclaredField("basePath");
            basePathField.setAccessible(true);
            basePathField.set(storageProvider, tempDir.toString());

            java.lang.reflect.Field baseUrlField = LocalFileStorageAdapter.class.getDeclaredField("baseUrl");
            baseUrlField.setAccessible(true);
            baseUrlField.set(storageProvider, "http://localhost:8084");
        } catch (Exception e) {
            throw new RuntimeException("Failed to set up test", e);
        }
    }

    @AfterEach
    void tearDown() {
        storageProvider = null;
    }

    @Test
    @DisplayName("Should store file successfully")
    void shouldStoreFileSuccessfully() throws FileStorageProvider.StorageException {
        // Given
        byte[] content = "Hello, World!".getBytes();
        String fileName = "test.txt";

        // When
        FileStorageProvider.StorageResult result = storageProvider.store(content, fileName);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.path()).isNotNull();
        assertThat(result.url()).isNotNull();
        assertThat(result.url()).startsWith("http://localhost:8084");

        // Verify file exists
        assertThat(Files.exists(Path.of(result.path()))).isTrue();
    }

    @Test
    @DisplayName("Should store file with date-based directory structure")
    void shouldStoreFileWithDateBasedDirectory() throws FileStorageProvider.StorageException {
        // Given
        byte[] content = "Test content".getBytes();
        String fileName = "document.pdf";

        // When
        FileStorageProvider.StorageResult result = storageProvider.store(content, fileName);

        // Then
        Path storedPath = Path.of(result.path());
        String relativePath = tempDir.relativize(storedPath).toString();

        // Should contain year/month/day structure
        assertThat(relativePath).matches("\\d{4}/\\d{2}/\\d{2}/[^/]+\\.pdf");
    }

    @ParameterizedTest
    @ValueSource(strings = {"document.pdf", "invoice.xml", "image.png", "data.json", "archive.zip"})
    @DisplayName("Should preserve file extension")
    void shouldPreserveFileExtension(String fileName) throws FileStorageProvider.StorageException {
        // Given
        byte[] content = "Test".getBytes();

        // When
        FileStorageProvider.StorageResult result = storageProvider.store(content, fileName);

        // Then
        assertThat(result.path()).endsWith(fileName.substring(fileName.lastIndexOf('.')));
    }

    @Test
    @DisplayName("Should generate unique filename for same file")
    void shouldGenerateUniqueFilename() throws FileStorageProvider.StorageException {
        // Given
        byte[] content = "Content".getBytes();
        String fileName = "same-name.txt";

        // When
        FileStorageProvider.StorageResult result1 = storageProvider.store(content, fileName);
        FileStorageProvider.StorageResult result2 = storageProvider.store(content, fileName);

        // Then
        assertThat(result1.path()).isNotEqualTo(result2.path());
    }

    @Test
    @DisplayName("Should retrieve stored file")
    void shouldRetrieveStoredFile() throws FileStorageProvider.StorageException {
        // Given
        byte[] originalContent = "Retrieval test content".getBytes();
        String fileName = "retrieve-test.txt";
        FileStorageProvider.StorageResult storedResult = storageProvider.store(originalContent, fileName);

        // When
        byte[] retrievedContent = storageProvider.retrieve(storedResult.path());

        // Then
        assertThat(retrievedContent).isEqualTo(originalContent);
    }

    @Test
    @DisplayName("Should throw exception when retrieving non-existent file")
    void shouldThrowWhenRetrievingNonExistentFile() {
        // Given
        String nonExistentPath = "/path/to/non-existent/file.txt";

        // When & Then
        assertThatThrownBy(() -> storageProvider.retrieve(nonExistentPath))
                .isInstanceOf(FileStorageProvider.StorageException.class)
                .hasMessageContaining("File not found");
    }

    @Test
    @DisplayName("Should delete stored file")
    void shouldDeleteStoredFile() throws FileStorageProvider.StorageException {
        // Given
        byte[] content = "Delete me".getBytes();
        String fileName = "delete-test.txt";
        FileStorageProvider.StorageResult storedResult = storageProvider.store(content, fileName);
        Path filePath = Path.of(storedResult.path());

        // When
        storageProvider.delete(storedResult.path());

        // Then
        assertThat(Files.exists(filePath)).isFalse();
    }

    @Test
    @DisplayName("Should not throw when deleting non-existent file")
    void shouldNotThrowWhenDeletingNonExistentFile() {
        // Given
        String nonExistentPath = "/path/to/non-existent/file.txt";

        // When & Then - should log warning but not throw
        assertThatCode(() -> storageProvider.delete(nonExistentPath))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should check if file exists")
    void shouldCheckIfFileExists() throws FileStorageProvider.StorageException {
        // Given
        byte[] content = "Existence check".getBytes();
        String fileName = "exists-test.txt";
        FileStorageProvider.StorageResult storedResult = storageProvider.store(content, fileName);

        // When & Then
        assertThat(storageProvider.exists(storedResult.path())).isTrue();
        assertThat(storageProvider.exists("/non/existent/path")).isFalse();
    }

    @Test
    @DisplayName("Should handle files without extension")
    void shouldHandleFilesWithoutExtension() throws FileStorageProvider.StorageException {
        // Given
        byte[] content = "No extension".getBytes();
        String fileName = "README";

        // When
        FileStorageProvider.StorageResult result = storageProvider.store(content, fileName);

        // Then
        assertThat(result.path()).doesNotEndWith(".");
        assertThat(Files.exists(Path.of(result.path()))).isTrue();
    }

    @Test
    @DisplayName("Should handle files with multiple extensions")
    void shouldHandleFilesWithMultipleExtensions() throws FileStorageProvider.StorageException {
        // Given
        byte[] content = "Multiple extensions".getBytes();
        String fileName = "archive.tar.gz";

        // When
        FileStorageProvider.StorageResult result = storageProvider.store(content, fileName);

        // Then - LocalFileStorageAdapter only keeps the last extension
        assertThat(result.path()).endsWith(".gz");
    }

    @Test
    @DisplayName("Should handle empty file")
    void shouldHandleEmptyFile() throws FileStorageProvider.StorageException, java.io.IOException {
        // Given
        byte[] content = new byte[0];
        String fileName = "empty.txt";

        // When
        FileStorageProvider.StorageResult result = storageProvider.store(content, fileName);

        // Then
        assertThat(Files.exists(Path.of(result.path()))).isTrue();
        assertThat(Files.size(Path.of(result.path()))).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle large file")
    void shouldHandleLargeFile() throws FileStorageProvider.StorageException, java.io.IOException {
        // Given - create a 5MB file
        byte[] content = new byte[5 * 1024 * 1024];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 256);
        }
        String fileName = "large.bin";

        // When
        FileStorageProvider.StorageResult result = storageProvider.store(content, fileName);

        // Then
        assertThat(Files.exists(Path.of(result.path()))).isTrue();
        assertThat(Files.size(Path.of(result.path()))).isEqualTo(content.length);
    }

    @Test
    @DisplayName("Should generate proper storage URL")
    void shouldGenerateProperStorageURL() throws FileStorageProvider.StorageException {
        // Given
        byte[] content = "URL test".getBytes();
        String fileName = "url-test.pdf";

        // When
        FileStorageProvider.StorageResult result = storageProvider.store(content, fileName);

        // Then
        assertThat(result.url()).startsWith("http://localhost:8084");
        assertThat(result.url()).contains("/documents/");
    }
}
