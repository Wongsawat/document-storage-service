package com.wpanther.storage.infrastructure.adapter.outbound.storage;

import com.wpanther.storage.domain.model.StorageException;
import com.wpanther.storage.domain.model.StorageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LocalFileStorageAdapter Tests")
class LocalFileStorageAdapterTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LocalFileStorageAdapter();
        ReflectionTestUtils.setField(adapter, "basePath", tempDir.toString());
        ReflectionTestUtils.setField(adapter, "baseUrl", "http://localhost:8084");
    }

    @Nested
    @DisplayName("store()")
    class StoreTests {

        @Test
        @DisplayName("Should store file successfully and return storage result")
        void shouldStoreFileSuccessfully() throws IOException {
            String documentId = "doc-123";
            byte[] content = "Test file content".getBytes();
            InputStream contentStream = new ByteArrayInputStream(content);
            String originalFilename = "test-document.pdf";
            long size = content.length;

            StorageResult result = adapter.store(documentId, contentStream, originalFilename, size);

            assertNotNull(result);
            assertNotNull(result.location());
            assertEquals("local", result.provider());
            assertNotNull(result.timestamp());
            assertTrue(Files.exists(Path.of(result.location())));
        }

        @Test
        @DisplayName("Should store file in YYYY/MM/DD directory structure")
        void shouldStoreFileInDateDirectoryStructure() throws IOException {
            String documentId = "doc-456";
            byte[] content = "Test content".getBytes();
            InputStream contentStream = new ByteArrayInputStream(content);
            String filename = "test.pdf";

            StorageResult result = adapter.store(documentId, contentStream, filename, content.length);

            String location = result.location();
            assertTrue(location.contains(tempDir.toString()));
            // Path should include date structure
            String relativePath = location.replace(tempDir.toString(), "");
            assertTrue(relativePath.matches(".*/\\d{4}/\\d{2}/\\d{2}/.*"));
        }

        @Test
        @DisplayName("Should preserve file extension from original filename")
        void shouldPreserveFileExtension() throws IOException {
            String documentId = "doc-789";
            byte[] content = "content".getBytes();
            InputStream contentStream = new ByteArrayInputStream(content);
            String filename = "document.PDF";

            StorageResult result = adapter.store(documentId, contentStream, filename, content.length);

            // The adapter returns the file path, not URL
            // Extension is preserved from original filename (lowercased)
            Path filePath = Path.of(result.location());
            String fileName = filePath.getFileName().toString();
            assertTrue(fileName.endsWith(".pdf") || fileName.endsWith(".PDF"));
        }

        @Test
        @DisplayName("Should handle files with no extension")
        void shouldHandleFileWithoutExtension() throws IOException {
            String documentId = "doc-no-ext";
            byte[] content = "content".getBytes();
            InputStream contentStream = new ByteArrayInputStream(content);
            String filename = "README";

            StorageResult result = adapter.store(documentId, contentStream, filename, content.length);

            assertNotNull(result.location());
            // File should still be stored, just without extension
            assertTrue(Files.exists(Path.of(result.location())));
        }

        @Test
        @DisplayName("Should handle files with multiple dots in filename")
        void shouldHandleMultipleDotsInFilename() throws IOException {
            String documentId = "doc-multi-dots";
            byte[] content = "content".getBytes();
            InputStream contentStream = new ByteArrayInputStream(content);
            String filename = "my.document.archive.tar.gz";

            StorageResult result = adapter.store(documentId, contentStream, filename, content.length);

            // Should use the last extension (.gz)
            assertTrue(result.location().endsWith(".gz"));
        }

        @Test
        @DisplayName("Should throw StorageException when file cannot be written")
        void shouldThrowExceptionWhenFileCannotBeWritten() throws IOException {
            // Create a file and use it as a directory (will cause failure)
            Path fileAsDir = tempDir.resolve("file-as-directory");
            Files.createFile(fileAsDir);

            LocalFileStorageAdapter invalidAdapter = new LocalFileStorageAdapter();
            ReflectionTestUtils.setField(invalidAdapter, "basePath", fileAsDir.toString());
            ReflectionTestUtils.setField(invalidAdapter, "baseUrl", "http://localhost:8084");

            String documentId = "doc-invalid";
            byte[] content = "content".getBytes();
            InputStream contentStream = new ByteArrayInputStream(content);

            assertThrows(StorageException.class, () -> invalidAdapter.store(documentId, contentStream, "test.txt", content.length));
        }
    }

    @Nested
    @DisplayName("retrieve()")
    class RetrieveTests {

        @Test
        @DisplayName("Should retrieve stored file successfully")
        void shouldRetrieveFileSuccessfully() throws IOException {
            String documentId = "doc-retrieve";
            byte[] originalContent = "Original content for retrieval".getBytes();
            InputStream contentStream = new ByteArrayInputStream(originalContent);
            String filename = "retrieve-test.pdf";

            StorageResult result = adapter.store(documentId, contentStream, filename, originalContent.length);
            String storageLocation = result.location();

            try (InputStream retrievedStream = adapter.retrieve(storageLocation)) {
                byte[] retrievedContent = retrievedStream.readAllBytes();
                assertArrayEquals(originalContent, retrievedContent);
            }
        }

        @Test
        @DisplayName("Should throw StorageException for non-existent file")
        void shouldThrowExceptionForNonExistentFile() {
            String nonExistentPath = tempDir.resolve("nonexistent/file.pdf").toString();

            assertThrows(StorageException.class, () -> adapter.retrieve(nonExistentPath));
        }

        @Test
        @DisplayName("Should throw NullPointerException for null path")
        void shouldThrowExceptionForNullPath() {
            assertThrows(NullPointerException.class, () -> adapter.retrieve((String) null));
        }
    }

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("Should delete existing file successfully")
        void shouldDeleteFileSuccessfully() throws IOException {
            String documentId = "doc-delete";
            byte[] content = "Content to delete".getBytes();
            InputStream contentStream = new ByteArrayInputStream(content);
            String filename = "delete-me.pdf";

            StorageResult result = adapter.store(documentId, contentStream, filename, content.length);
            String storageLocation = result.location();

            assertTrue(Files.exists(Path.of(storageLocation)));

            adapter.delete(storageLocation);

            assertFalse(Files.exists(Path.of(storageLocation)));
        }

        @Test
        @DisplayName("Should not throw exception when deleting non-existent file")
        void shouldNotThrowWhenDeletingNonExistentFile() {
            String nonExistentPath = tempDir.resolve("nonexistent/file.pdf").toString();

            assertDoesNotThrow(() -> adapter.delete(nonExistentPath));
        }

        @Test
        @DisplayName("Should throw StorageException for empty path")
        void shouldThrowExceptionForEmptyPath() {
            assertThrows(StorageException.class, () -> adapter.delete(""));
        }

        @Test
        @DisplayName("Should throw NullPointerException for null path")
        void shouldThrowExceptionForNullPath() {
            assertThrows(NullPointerException.class, () -> adapter.delete(null));
        }
    }

    @Nested
    @DisplayName("exists()")
    class ExistsTests {

        @Test
        @DisplayName("Should return true for existing file")
        void shouldReturnTrueForExistingFile() throws IOException {
            String documentId = "doc-exists";
            byte[] content = "Check existence".getBytes();
            InputStream contentStream = new ByteArrayInputStream(content);
            String filename = "exists-check.pdf";

            StorageResult result = adapter.store(documentId, contentStream, filename, content.length);

            assertTrue(adapter.exists(result.location()));
        }

        @Test
        @DisplayName("Should return false for non-existent file")
        void shouldReturnFalseForNonExistentFile() {
            String nonExistentPath = tempDir.resolve("nonexistent/file.pdf").toString();
            assertFalse(adapter.exists(nonExistentPath));
        }

        @Test
        @DisplayName("Should throw NullPointerException for null path")
        void shouldThrowExceptionForNullPath() {
            assertThrows(NullPointerException.class, () -> adapter.exists(null));
        }
    }
}
