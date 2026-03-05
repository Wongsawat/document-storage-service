package com.wpanther.storage.infrastructure.adapter.inbound.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DocumentValidator Tests")
class DocumentValidatorTest {

    @Nested
    @DisplayName("validateInvoiceId()")
    class ValidateInvoiceIdTests {

        @Test
        @DisplayName("Should accept valid invoice ID")
        void shouldAcceptValidInvoiceId() {
            assertDoesNotThrow(() -> DocumentValidator.validateInvoiceId("INV-001"));
            assertDoesNotThrow(() -> DocumentValidator.validateInvoiceId("INV_2024_001"));
            assertDoesNotThrow(() -> DocumentValidator.validateInvoiceId("invoice-123"));
            assertDoesNotThrow(() -> DocumentValidator.validateInvoiceId("ABC123XYZ"));
        }

        @Test
        @DisplayName("Should accept invoice ID at max length (100 chars)")
        void shouldAcceptMaxLengthInvoiceId() {
            String maxId = "A".repeat(100);
            assertDoesNotThrow(() -> DocumentValidator.validateInvoiceId(maxId));
        }

        @Test
        @DisplayName("Should reject null invoice ID")
        void shouldRejectNullInvoiceId() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DocumentValidator.validateInvoiceId(null)
            );
            assertTrue(exception.getMessage().contains("Invoice ID cannot be empty"));
        }

        @Test
        @DisplayName("Should reject empty invoice ID")
        void shouldRejectEmptyInvoiceId() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DocumentValidator.validateInvoiceId("")
            );
            assertTrue(exception.getMessage().contains("Invoice ID cannot be empty"));
        }

        @Test
        @DisplayName("Should reject whitespace-only invoice ID")
        void shouldRejectWhitespaceInvoiceId() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DocumentValidator.validateInvoiceId("   ")
            );
            assertTrue(exception.getMessage().contains("Invoice ID cannot be empty"));
        }

        @Test
        @DisplayName("Should reject invoice ID with special characters")
        void shouldRejectInvalidCharacters() {
            assertThrows(IllegalArgumentException.class, () -> DocumentValidator.validateInvoiceId("INV/001"));
            assertThrows(IllegalArgumentException.class, () -> DocumentValidator.validateInvoiceId("INV.001"));
            assertThrows(IllegalArgumentException.class, () -> DocumentValidator.validateInvoiceId("INV@001"));
            assertThrows(IllegalArgumentException.class, () -> DocumentValidator.validateInvoiceId("INV 001"));
        }

        @Test
        @DisplayName("Should reject invoice ID exceeding max length")
        void shouldRejectTooLongInvoiceId() {
            String tooLongId = "A".repeat(101);
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DocumentValidator.validateInvoiceId(tooLongId)
            );
            assertTrue(exception.getMessage().contains("1-100 alphanumeric"));
        }
    }

    @Nested
    @DisplayName("validateInvoiceNumber()")
    class ValidateInvoiceNumberTests {

        @Test
        @DisplayName("Should accept valid invoice number")
        void shouldAcceptValidInvoiceNumber() {
            assertDoesNotThrow(() -> DocumentValidator.validateInvoiceNumber("INV-2024-001"));
            assertDoesNotThrow(() -> DocumentValidator.validateInvoiceNumber("INV/2024/001"));
            assertDoesNotThrow(() -> DocumentValidator.validateInvoiceNumber("INV_2024.001"));
            assertDoesNotThrow(() -> DocumentValidator.validateInvoiceNumber("ABC123"));
        }

        @Test
        @DisplayName("Should accept invoice number at max length (50 chars)")
        void shouldAcceptMaxLengthInvoiceNumber() {
            String maxNumber = "A".repeat(50);
            assertDoesNotThrow(() -> DocumentValidator.validateInvoiceNumber(maxNumber));
        }

        @Test
        @DisplayName("Should accept null invoice number (optional field)")
        void shouldAcceptNullInvoiceNumber() {
            assertDoesNotThrow(() -> DocumentValidator.validateInvoiceNumber(null));
        }

        @Test
        @DisplayName("Should accept empty invoice number (optional field)")
        void shouldAcceptEmptyInvoiceNumber() {
            assertDoesNotThrow(() -> DocumentValidator.validateInvoiceNumber(""));
            assertDoesNotThrow(() -> DocumentValidator.validateInvoiceNumber("   "));
        }

        @Test
        @DisplayName("Should reject invoice number with invalid special characters")
        void shouldRejectInvalidCharacters() {
            assertThrows(IllegalArgumentException.class, () -> DocumentValidator.validateInvoiceNumber("INV@2024"));
            assertThrows(IllegalArgumentException.class, () -> DocumentValidator.validateInvoiceNumber("INV#001"));
            assertThrows(IllegalArgumentException.class, () -> DocumentValidator.validateInvoiceNumber("INV 001"));
        }

        @Test
        @DisplayName("Should reject invoice number exceeding max length")
        void shouldRejectTooLongInvoiceNumber() {
            String tooLong = "A".repeat(51);
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DocumentValidator.validateInvoiceNumber(tooLong)
            );
            assertTrue(exception.getMessage().contains("1-50 characters"));
        }
    }

    @Nested
    @DisplayName("validateDocumentId()")
    class ValidateDocumentIdTests {

        @Test
        @DisplayName("Should accept valid UUID format")
        void shouldAcceptValidUuid() {
            assertDoesNotThrow(() -> DocumentValidator.validateDocumentId("550e8400-e29b-41d4-a716-446655440000"));
            assertDoesNotThrow(() -> DocumentValidator.validateDocumentId("00000000-0000-0000-0000-000000000000"));
            assertDoesNotThrow(() -> DocumentValidator.validateDocumentId("FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"));
        }

        @Test
        @DisplayName("Should reject null document ID")
        void shouldRejectNullDocumentId() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DocumentValidator.validateDocumentId(null)
            );
            assertTrue(exception.getMessage().contains("Document ID cannot be empty"));
        }

        @Test
        @DisplayName("Should reject empty document ID")
        void shouldRejectEmptyDocumentId() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DocumentValidator.validateDocumentId("")
            );
            assertTrue(exception.getMessage().contains("Document ID cannot be empty"));
        }

        @Test
        @DisplayName("Should reject non-UUID format")
        void shouldRejectNonUuidFormat() {
            assertThrows(IllegalArgumentException.class, () -> DocumentValidator.validateDocumentId("doc-123"));
            assertThrows(IllegalArgumentException.class, () -> DocumentValidator.validateDocumentId("12345"));
            assertThrows(IllegalArgumentException.class, () -> DocumentValidator.validateDocumentId("550e8400-e29b-41d4-a716"));
            assertThrows(IllegalArgumentException.class, () -> DocumentValidator.validateDocumentId("g50e8400-e29b-41d4-a716-446655440000"));
        }
    }

    @Nested
    @DisplayName("validateFile()")
    class ValidateFileTests {

        @Test
        @DisplayName("Should accept valid file")
        void shouldAcceptValidFile() {
            byte[] content = "PDF content".getBytes();
            MultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                content
            );
            assertDoesNotThrow(() -> DocumentValidator.validateFile(file));
        }

        @Test
        @DisplayName("Should accept file up to max size (100MB)")
        void shouldAcceptMaxSizeFile() {
            byte[] content = new byte[100 * 1024 * 1024]; // exactly 100MB
            MultipartFile file = new MockMultipartFile(
                "file",
                "large.pdf",
                "application/pdf",
                content
            );
            assertDoesNotThrow(() -> DocumentValidator.validateFile(file));
        }

        @Test
        @DisplayName("Should reject null file")
        void shouldRejectNullFile() {
            assertThrows(
                IllegalArgumentException.class,
                () -> DocumentValidator.validateFile(null)
            );
        }

        @Test
        @DisplayName("Should reject empty file")
        void shouldRejectEmptyFile() {
            MultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
            );
            assertThrows(
                IllegalArgumentException.class,
                () -> DocumentValidator.validateFile(emptyFile)
            );
        }

        @Test
        @DisplayName("Should reject file exceeding max size")
        void shouldRejectTooLargeFile() {
            byte[] content = new byte[100 * 1024 * 1024 + 1]; // 100MB + 1 byte
            MultipartFile file = new MockMultipartFile(
                "file",
                "huge.pdf",
                "application/pdf",
                content
            );
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DocumentValidator.validateFile(file)
            );
            assertTrue(exception.getMessage().contains("100MB"));
        }

        @Test
        @DisplayName("Should reject file with empty filename")
        void shouldRejectEmptyFilename() {
            byte[] content = "content".getBytes();
            MultipartFile file = new MockMultipartFile(
                "file",
                "",
                "application/pdf",
                content
            );
            assertThrows(
                IllegalArgumentException.class,
                () -> DocumentValidator.validateFile(file)
            );
        }

        @Test
        @DisplayName("Should reject file with null filename")
        void shouldRejectNullFilename() {
            byte[] content = "content".getBytes();
            MultipartFile file = new MockMultipartFile(
                "file",
                null,
                "application/pdf",
                content
            );
            assertThrows(
                IllegalArgumentException.class,
                () -> DocumentValidator.validateFile(file)
            );
        }
    }
}
