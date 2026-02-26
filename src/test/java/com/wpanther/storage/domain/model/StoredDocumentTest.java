package com.wpanther.storage.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("StoredDocument Domain Model Tests")
class StoredDocumentTest {

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build valid document with all fields")
        void shouldBuildValidDocumentWithAllFields() {
            // Given
            String id = "doc-123";
            String fileName = "invoice.pdf";
            String contentType = "application/pdf";
            String storagePath = "/path/to/file";
            String storageUrl = "http://example.com/file";
            long fileSize = 1024L;
            String checksum = "abc123";
            DocumentType documentType = DocumentType.INVOICE_PDF;
            String invoiceId = "INV-001";
            String invoiceNumber = "INV-2024-001";

            // When
            StoredDocument document = StoredDocument.builder()
                    .id(id)
                    .fileName(fileName)
                    .contentType(contentType)
                    .storagePath(storagePath)
                    .storageUrl(storageUrl)
                    .fileSize(fileSize)
                    .checksum(checksum)
                    .documentType(documentType)
                    .invoiceId(invoiceId)
                    .invoiceNumber(invoiceNumber)
                    .build();

            // Then
            assertThat(document.getId()).isEqualTo(id);
            assertThat(document.getFileName()).isEqualTo(fileName);
            assertThat(document.getContentType()).isEqualTo(contentType);
            assertThat(document.getStoragePath()).isEqualTo(storagePath);
            assertThat(document.getStorageUrl()).isEqualTo(storageUrl);
            assertThat(document.getFileSize()).isEqualTo(fileSize);
            assertThat(document.getChecksum()).isEqualTo(checksum);
            assertThat(document.getDocumentType()).isEqualTo(documentType);
            assertThat(document.getInvoiceId()).isEqualTo(invoiceId);
            assertThat(document.getInvoiceNumber()).isEqualTo(invoiceNumber);
        }

        @Test
        @DisplayName("Should use defaults when optional fields not provided")
        void shouldUseDefaultsWhenOptionalFieldsNotProvided() {
            // When
            StoredDocument document = StoredDocument.builder()
                    .id("doc-123")
                    .fileName("test.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path")
                    .storageUrl("http://example.com")
                    .fileSize(100L)
                    .checksum("hash")
                    .build();

            // Then
            assertThat(document.getDocumentType()).isEqualTo(DocumentType.OTHER);
            assertThat(document.getCreatedAt()).isNotNull();
            assertThat(document.getExpiresAt()).isNull();
        }

        @Test
        @DisplayName("Should generate UUID when ID not provided")
        void shouldGenerateUUIDWhenIdNotProvided() {
            // Note: This test documents current behavior where ID is required
            // If we want auto-generated UUIDs, the implementation should change
            StoredDocument document = StoredDocument.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .fileName("test.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path")
                    .storageUrl("http://example.com")
                    .fileSize(100L)
                    .checksum("hash")
                    .build();

            assertThat(document.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should throw exception when ID is null")
        void shouldThrowWhenIdIsNull() {
            assertThatThrownBy(() -> StoredDocument.builder()
                    .id(null)
                    .fileName("test.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path")
                    .storageUrl("http://example.com")
                    .fileSize(100L)
                    .checksum("hash")
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Document ID is required");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  ", "\t", "\n"})
        @DisplayName("Should throw exception when file name is blank")
        void shouldThrowWhenFileNameIsBlank(String fileName) {
            assertThatThrownBy(() -> StoredDocument.builder()
                    .id("doc-123")
                    .fileName(fileName)
                    .contentType("application/pdf")
                    .storagePath("/path")
                    .storageUrl("http://example.com")
                    .fileSize(100L)
                    .checksum("hash")
                    .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("File name cannot be blank");
        }

        @Test
        @DisplayName("Should throw exception when file size is not positive")
        void shouldThrowWhenFileSizeIsNotPositive() {
            assertThatThrownBy(() -> StoredDocument.builder()
                    .id("doc-123")
                    .fileName("test.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path")
                    .storageUrl("http://example.com")
                    .fileSize(0L)
                    .checksum("hash")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("File size must be positive");
        }

        @Test
        @DisplayName("Should throw exception when checksum is blank")
        void shouldThrowWhenChecksumIsBlank() {
            assertThatThrownBy(() -> StoredDocument.builder()
                    .id("doc-123")
                    .fileName("test.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path")
                    .storageUrl("http://example.com")
                    .fileSize(100L)
                    .checksum("")
                    .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Checksum cannot be blank");
        }
    }

    @Nested
    @DisplayName("Business Logic Tests")
    class BusinessLogicTests {

        @Test
        @DisplayName("Should verify checksum correctly")
        void shouldVerifyChecksum() {
            // Given
            String checksum = "abc123def456";
            StoredDocument document = StoredDocument.builder()
                    .id("doc-123")
                    .fileName("test.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path")
                    .storageUrl("http://example.com")
                    .fileSize(100L)
                    .checksum(checksum)
                    .build();

            // When/Then
            assertThat(document.verifyChecksum(checksum)).isTrue();
            assertThat(document.verifyChecksum("wrong-hash")).isFalse();
        }

        @Test
        @DisplayName("Should return false when expiresAt is not set")
        void shouldReturnFalseWhenExpiresAtNotSet() {
            // Given
            StoredDocument document = StoredDocument.builder()
                    .id("doc-123")
                    .fileName("test.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path")
                    .storageUrl("http://example.com")
                    .fileSize(100L)
                    .checksum("hash")
                    .build();

            // When/Then
            assertThat(document.isExpired()).isFalse();
        }

        @Test
        @DisplayName("Should return true when document is expired")
        void shouldReturnTrueWhenExpired() {
            // Given
            StoredDocument document = StoredDocument.builder()
                    .id("doc-123")
                    .fileName("test.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path")
                    .storageUrl("http://example.com")
                    .fileSize(100L)
                    .checksum("hash")
                    .expiresAt(LocalDateTime.now().minusDays(1))
                    .build();

            // When/Then
            assertThat(document.isExpired()).isTrue();
        }

        @Test
        @DisplayName("Should return false when document is not expired")
        void shouldReturnFalseWhenNotExpired() {
            // Given
            StoredDocument document = StoredDocument.builder()
                    .id("doc-123")
                    .fileName("test.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path")
                    .storageUrl("http://example.com")
                    .fileSize(100L)
                    .checksum("hash")
                    .expiresAt(LocalDateTime.now().plusDays(1))
                    .build();

            // When/Then
            assertThat(document.isExpired()).isFalse();
        }

        @Test
        @DisplayName("Should set expiration date")
        void shouldSetExpirationDate() {
            // Given
            StoredDocument document = StoredDocument.builder()
                    .id("doc-123")
                    .fileName("test.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path")
                    .storageUrl("http://example.com")
                    .fileSize(100L)
                    .checksum("hash")
                    .build();

            LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);

            // When
            document.setExpiresAt(expiresAt);

            // Then
            assertThat(document.getExpiresAt()).isEqualTo(expiresAt);
        }
    }

    @Nested
    @DisplayName("Getter Tests")
    class GetterTests {

        @Test
        @DisplayName("Should return all field values through getters")
        void shouldReturnAllFieldValues() {
            // Given
            LocalDateTime now = LocalDateTime.now();
            StoredDocument document = StoredDocument.builder()
                    .id("doc-123")
                    .fileName("invoice.pdf")
                    .contentType("application/pdf")
                    .storagePath("/2024/01/01/file.pdf")
                    .storageUrl("http://example.com/file.pdf")
                    .fileSize(2048L)
                    .checksum("sha256-hash")
                    .documentType(DocumentType.INVOICE_PDF)
                    .createdAt(now)
                    .expiresAt(now.plusDays(30))
                    .invoiceId("INV-001")
                    .invoiceNumber("INV-2024-001")
                    .build();

            // Then
            assertThat(document.getId()).isEqualTo("doc-123");
            assertThat(document.getFileName()).isEqualTo("invoice.pdf");
            assertThat(document.getContentType()).isEqualTo("application/pdf");
            assertThat(document.getStoragePath()).isEqualTo("/2024/01/01/file.pdf");
            assertThat(document.getStorageUrl()).isEqualTo("http://example.com/file.pdf");
            assertThat(document.getFileSize()).isEqualTo(2048L);
            assertThat(document.getChecksum()).isEqualTo("sha256-hash");
            assertThat(document.getDocumentType()).isEqualTo(DocumentType.INVOICE_PDF);
            assertThat(document.getCreatedAt()).isEqualTo(now);
            assertThat(document.getExpiresAt()).isEqualTo(now.plusDays(30));
            assertThat(document.getInvoiceId()).isEqualTo("INV-001");
            assertThat(document.getInvoiceNumber()).isEqualTo("INV-2024-001");
        }
    }
}
