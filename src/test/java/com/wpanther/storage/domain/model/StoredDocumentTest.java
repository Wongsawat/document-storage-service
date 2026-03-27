package com.wpanther.storage.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StoredDocument Tests")
class StoredDocumentTest {

    private StoredDocument createValidDocument() {
        return StoredDocument.builder()
            .id("doc-1")
            .fileName("test.pdf")
            .contentType("application/pdf")
            .storagePath("/path/to/doc")
            .storageUrl("http://example.com/doc")
            .fileSize(1024L)
            .checksum("sha256-abc")
            .documentType(DocumentType.INVOICE_PDF)
            .invoiceId("INV-001")
            .invoiceNumber("INV-2024-001")
            .build();
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("withExpiresAt() returns new instance without modifying original")
        void withExpiresAtReturnsNewInstance() {
            // Given
            StoredDocument original = createValidDocument();
            LocalDateTime expiration = LocalDateTime.now().plusDays(30);

            // When
            StoredDocument updated = original.withExpiresAt(expiration);

            // Then
            assertThat(updated).isNotSameAs(original);
            assertThat(original.getExpiresAt()).isNull();
            assertThat(updated.getExpiresAt()).isEqualTo(expiration);

            // All other fields preserved
            assertThat(updated.getId()).isEqualTo(original.getId());
            assertThat(updated.getFileName()).isEqualTo(original.getFileName());
            assertThat(updated.getInvoiceId()).isEqualTo(original.getInvoiceId());
            assertThat(updated.getInvoiceNumber()).isEqualTo(original.getInvoiceNumber());
        }

        @Test
        @DisplayName("withExpiresAt() preserves existing expiration when called on already-expiring doc")
        void withExpiresAtOverwritesExistingExpiration() {
            // Given
            StoredDocument original = StoredDocument.builder()
                .id("doc-1")
                .fileName("test.pdf")
                .contentType("application/pdf")
                .storagePath("/path/to/doc")
                .storageUrl("http://example.com/doc")
                .fileSize(1024L)
                .checksum("sha256-abc")
                .expiresAt(LocalDateTime.now().plusDays(10))
                .build();
            LocalDateTime newExpiration = LocalDateTime.now().plusDays(60);

            // When
            StoredDocument updated = original.withExpiresAt(newExpiration);

            // Then
            assertThat(original.getExpiresAt()).isNotEqualTo(newExpiration);
            assertThat(updated.getExpiresAt()).isEqualTo(newExpiration);
        }
    }

    @Nested
    @DisplayName("isExpired()")
    class IsExpiredTests {

        @Test
        @DisplayName("Should return false when no expiration set")
        void shouldReturnFalseWhenNoExpiration() {
            StoredDocument doc = createValidDocument();
            assertThat(doc.isExpired()).isFalse();
        }

        @Test
        @DisplayName("Should return false when expiration is in the future")
        void shouldReturnFalseWhenExpirationInFuture() {
            StoredDocument doc = StoredDocument.builder()
                .id("doc-1")
                .fileName("test.pdf")
                .contentType("application/pdf")
                .storagePath("/path/to/doc")
                .storageUrl("http://example.com/doc")
                .fileSize(1024L)
                .checksum("sha256-abc")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
            assertThat(doc.isExpired()).isFalse();
        }

        @Test
        @DisplayName("Should return true when expiration is in the past")
        void shouldReturnTrueWhenExpirationInPast() {
            StoredDocument doc = StoredDocument.builder()
                .id("doc-1")
                .fileName("test.pdf")
                .contentType("application/pdf")
                .storagePath("/path/to/doc")
                .storageUrl("http://example.com/doc")
                .fileSize(1024L)
                .checksum("sha256-abc")
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
            assertThat(doc.isExpired()).isTrue();
        }
    }
}
