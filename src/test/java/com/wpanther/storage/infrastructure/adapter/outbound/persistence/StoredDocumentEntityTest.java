package com.wpanther.storage.infrastructure.adapter.outbound.persistence;

import com.wpanther.storage.domain.model.DocumentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StoredDocumentEntity Tests")
class StoredDocumentEntityTest {

    private StoredDocumentEntity entity;

    @BeforeEach
    void setUp() {
        entity = new StoredDocumentEntity();
    }

    @AfterEach
    void tearDown() {
        entity = null;
    }

    @Nested
    @DisplayName("Default state after construction")
    class DefaultStateTests {

        @Test
        @DisplayName("Should have null values by default")
        void shouldHaveNullValuesByDefault() {
            StoredDocumentEntity entity = new StoredDocumentEntity();

            assertNull(entity.getId());
            assertNull(entity.getFileName());
            assertNull(entity.getContentType());
            assertNull(entity.getStoragePath());
            assertNull(entity.getStorageUrl());
            assertNull(entity.getFileSize());
            assertNull(entity.getChecksum());
            assertNull(entity.getDocumentType());
            assertNull(entity.getCreatedAt());
            assertNull(entity.getExpiresAt());
            assertNull(entity.getInvoiceId());
            assertNull(entity.getInvoiceNumber());
        }
    }

    @Nested
    @DisplayName("Setter methods")
    class SetterTests {

        @Test
        @DisplayName("Should set and get id")
        void shouldSetAndGetId() {
            entity.setId("test-id-123");
            assertEquals("test-id-123", entity.getId());
        }

        @Test
        @DisplayName("Should set and get fileName")
        void shouldSetAndGetFileName() {
            entity.setFileName("invoice.pdf");
            assertEquals("invoice.pdf", entity.getFileName());
        }

        @Test
        @DisplayName("Should set and get contentType")
        void shouldSetAndGetContentType() {
            entity.setContentType("application/pdf");
            assertEquals("application/pdf", entity.getContentType());
        }

        @Test
        @DisplayName("Should set and get storagePath")
        void shouldSetAndGetStoragePath() {
            entity.setStoragePath("/var/documents/2024/03/05/file-123.pdf");
            assertEquals("/var/documents/2024/03/05/file-123.pdf", entity.getStoragePath());
        }

        @Test
        @DisplayName("Should set and get storageUrl")
        void shouldSetAndGetStorageUrl() {
            entity.setStorageUrl("http://localhost:8084/api/v1/documents/file-123.pdf");
            assertEquals("http://localhost:8084/api/v1/documents/file-123.pdf", entity.getStorageUrl());
        }

        @Test
        @DisplayName("Should set and get fileSize")
        void shouldSetAndGetFileSize() {
            entity.setFileSize(1024L);
            assertEquals(1024L, entity.getFileSize());
        }

        @Test
        @DisplayName("Should set and get checksum")
        void shouldSetAndGetChecksum() {
            entity.setChecksum("abc123def456");
            assertEquals("abc123def456", entity.getChecksum());
        }

        @Test
        @DisplayName("Should set and get documentType")
        void shouldSetAndGetDocumentType() {
            entity.setDocumentType(DocumentType.INVOICE_PDF);
            assertEquals(DocumentType.INVOICE_PDF, entity.getDocumentType());
        }

        @Test
        @DisplayName("Should set and get createdAt")
        void shouldSetAndGetCreatedAt() {
            LocalDateTime now = LocalDateTime.now();
            entity.setCreatedAt(now);
            assertEquals(now, entity.getCreatedAt());
        }

        @Test
        @DisplayName("Should set and get expiresAt")
        void shouldSetAndGetExpiresAt() {
            var Later = LocalDateTime.now().plusDays(30);
            entity.setExpiresAt(Later);
            assertEquals(Later, entity.getExpiresAt());
        }

        @Test
        @DisplayName("Should set and get invoiceId")
        void shouldSetAndGetInvoiceId() {
            entity.setInvoiceId("INV-001");
            assertEquals("INV-001", entity.getInvoiceId());
        }

        @Test
        @DisplayName("Should set and get invoiceNumber")
        void shouldSetAndGetInvoiceNumber() {
            entity.setInvoiceNumber("INV-2024-001");
            assertEquals("INV-2024-001", entity.getInvoiceNumber());
        }
    }

    @Nested
    @DisplayName("Builder pattern")
    class BuilderTests {

        @Test
        @DisplayName("Should build entity with all fields")
        void shouldBuildEntityWithAllFields() {
            LocalDateTime now = LocalDateTime.now();
            DocumentType type = DocumentType.SIGNED_XML;

            StoredDocumentEntity built = StoredDocumentEntity.builder()
                .id("doc-123")
                .fileName("tax-invoice.pdf")
                .contentType("application/pdf")
                .storagePath("/path/to/file.pdf")
                .storageUrl("http://localhost:8084/api/v1/documents/doc-123.pdf")
                .fileSize(2048L)
                .checksum("sha256-hash")
                .documentType(type)
                .createdAt(now)
                .expiresAt(now.plusDays(30))
                .invoiceId("INV-001")
                .invoiceNumber("INV-2024-001")
                .build();

            assertEquals("doc-123", built.getId());
            assertEquals("tax-invoice.pdf", built.getFileName());
            assertEquals("application/pdf", built.getContentType());
            assertEquals("/path/to/file.pdf", built.getStoragePath());
            assertEquals("http://localhost:8084/api/v1/documents/doc-123.pdf", built.getStorageUrl());
            assertEquals(2048L, built.getFileSize());
            assertEquals("sha256-hash", built.getChecksum());
            assertEquals(type, built.getDocumentType());
            assertEquals(now, built.getCreatedAt());
            assertEquals(now.plusDays(30), built.getExpiresAt());
            assertEquals("INV-001", built.getInvoiceId());
            assertEquals("INV-2024-001", built.getInvoiceNumber());
        }

        @Test
        @DisplayName("Should build entity with minimal fields")
        void shouldBuildEntityWithMinimalFields() {
            StoredDocumentEntity built = StoredDocumentEntity.builder()
                .id("minimal-doc")
                .fileName("minimal.pdf")
                .build();

            assertEquals("minimal-doc", built.getId());
            assertEquals("minimal.pdf", built.getFileName());
        }
    }

    @Nested
    @DisplayName("DocumentType enum values")
    class DocumentTypeTests {

        @Test
        @DisplayName("Should accept all DocumentType enum values")
        void shouldAcceptAllDocumentTypeValues() {
            DocumentType[] types = DocumentType.values();

            for (DocumentType type : types) {
                assertDoesNotThrow(() -> {
                    StoredDocumentEntity entity = StoredDocumentEntity.builder()
                        .documentType(type)
                        .build();
                    assertEquals(type, entity.getDocumentType());
                });
            }
        }
    }
}
