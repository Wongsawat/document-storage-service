package com.wpanther.storage.infrastructure.adapter.outbound.storage;

import com.wpanther.storage.domain.model.StorageException;
import com.wpanther.storage.domain.model.StorageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3FileStorageAdapter Tests")
class S3FileStorageAdapterTest {

    @Mock
    private S3Client mockS3Client;

    private S3FileStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        // Create adapter with test configuration
        // Note: We need to use reflection or create a test-specific constructor
        // For now, we'll create a minimal adapter that uses our mock
        adapter = new S3FileStorageAdapter(
            "test-bucket",
            "us-east-1",
            "test-access-key",
            "test-secret-key",
            "https://s3.amazonaws.com",
            "",
            false
        );

        // Replace the S3Client with our mock using reflection
        try {
            java.lang.reflect.Field field = S3FileStorageAdapter.class.getDeclaredField("s3Client");
            field.setAccessible(true);
            field.set(adapter, mockS3Client);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock S3Client", e);
        }
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

            PutObjectResponse mockResponse = PutObjectResponse.builder()
                .eTag("test-etag")
                .build();

            when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(mockResponse);

            StorageResult result = adapter.store(documentId, contentStream, originalFilename, size);

            assertNotNull(result);
            assertNotNull(result.location());
            assertEquals("s3", result.provider());
            assertNotNull(result.timestamp());

            verify(mockS3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("Should generate S3 key with date structure and UUID")
        void shouldGenerateS3KeyWithDateStructure() throws IOException {
            String documentId = "doc-456";
            byte[] content = "Test content".getBytes();
            InputStream contentStream = new ByteArrayInputStream(content);
            String filename = "test.pdf";

            when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

            StorageResult result = adapter.store(documentId, contentStream, filename, content.length);

            // Key should be in format: YYYY/MM/DD/UUID_filename
            String key = result.location();
            assertTrue(key.matches("\\d{4}/\\d{2}/\\d{2}/[a-f0-9\\-]+_" + filename));
        }

        @Test
        @DisplayName("Should throw StorageException when S3 putObject fails")
        void shouldThrowExceptionWhenPutObjectFails() throws IOException {
            String documentId = "doc-error";
            byte[] content = "content".getBytes();
            InputStream contentStream = new ByteArrayInputStream(content);
            String filename = "test.txt";

            when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("Access Denied").statusCode(403).build());

            assertThrows(StorageException.class, () ->
                adapter.store(documentId, contentStream, filename, content.length));
        }

        @Test
        @DisplayName("Should throw StorageException when InputStream read fails")
        void shouldThrowExceptionWhenStreamReadFails() throws IOException {
            String documentId = "doc-stream-error";
            InputStream errorStream = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("Stream read error");
                }
            };
            String filename = "test.txt";

            assertThrows(StorageException.class, () ->
                adapter.store(documentId, errorStream, filename, 100));
        }
    }

    @Nested
    @DisplayName("retrieve()")
    class RetrieveTests {

        @Test
        @DisplayName("Should retrieve stored file successfully")
        void shouldRetrieveFileSuccessfully() throws IOException {
            String storageKey = "2024/03/05/uuid-123_test-document.pdf";
            byte[] content = "Original content for retrieval".getBytes();

            // Create a ResponseInputStream that wraps our content
            software.amazon.awssdk.core.ResponseInputStream<GetObjectResponse> responseStream =
                new software.amazon.awssdk.core.ResponseInputStream<>(
                    GetObjectResponse.builder().contentLength((long) content.length).build(),
                    new ByteArrayInputStream(content)
                );

            when(mockS3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(responseStream);

            try (InputStream retrievedStream = adapter.retrieve(storageKey)) {
                byte[] retrievedContent = retrievedStream.readAllBytes();
                assertArrayEquals(content, retrievedContent);
            }

            verify(mockS3Client).getObject(any(GetObjectRequest.class));
        }

        @Test
        @DisplayName("Should throw StorageException for non-existent file (NoSuchKeyException)")
        void shouldThrowExceptionForNonExistentFile() {
            String storageKey = "nonexistent/file.pdf";

            when(mockS3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder()
                    .message("Key not found")
                    .statusCode(404)
                    .build());

            assertThrows(StorageException.class, () -> adapter.retrieve(storageKey));
        }

        @Test
        @DisplayName("Should throw StorageException for S3 errors")
        void shouldThrowExceptionForS3Errors() {
            String storageKey = "error/file.pdf";

            when(mockS3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder()
                    .message("Internal Server Error")
                    .statusCode(500)
                    .build());

            assertThrows(StorageException.class, () -> adapter.retrieve(storageKey));
        }
    }

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("Should delete existing file successfully")
        void shouldDeleteFileSuccessfully() {
            String storageKey = "2024/03/05/uuid-delete_delete-me.pdf";

            when(mockS3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

            assertDoesNotThrow(() -> adapter.delete(storageKey));

            verify(mockS3Client).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("Should throw StorageException when delete fails")
        void shouldThrowExceptionWhenDeleteFails() {
            String storageKey = "error/file.pdf";

            when(mockS3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder()
                    .message("Access Denied")
                    .statusCode(403)
                    .build());

            assertThrows(StorageException.class, () -> adapter.delete(storageKey));
        }
    }

    @Nested
    @DisplayName("exists()")
    class ExistsTests {

        @Test
        @DisplayName("Should return true for existing file")
        void shouldReturnTrueForExistingFile() {
            String storageKey = "2024/03/05/uuid-exists_exists-check.pdf";

            when(mockS3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                    .contentLength(1024L)
                    .build());

            assertTrue(adapter.exists(storageKey));

            verify(mockS3Client).headObject(any(HeadObjectRequest.class));
        }

        @Test
        @DisplayName("Should return false for non-existent file (NoSuchKeyException)")
        void shouldReturnFalseForNonExistentFile() {
            String storageKey = "nonexistent/file.pdf";

            when(mockS3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder()
                    .message("Not Found")
                    .statusCode(404)
                    .build());

            assertFalse(adapter.exists(storageKey));
        }

        @Test
        @DisplayName("Should return false for S3 errors")
        void shouldReturnFalseForS3Errors() {
            String storageKey = "error/file.pdf";

            when(mockS3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder()
                    .message("Service Unavailable")
                    .statusCode(503)
                    .build());

            assertFalse(adapter.exists(storageKey));
        }
    }
}
