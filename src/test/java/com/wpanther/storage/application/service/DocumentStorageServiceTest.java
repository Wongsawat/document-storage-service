package com.wpanther.storage.application.service;

import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.service.FileStorageProvider;
import com.wpanther.storage.infrastructure.persistence.MongoDocumentRepository;
import com.wpanther.storage.infrastructure.persistence.StoredDocumentEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentStorageService Tests")
class DocumentStorageServiceTest {

    @Mock
    private MongoDocumentRepository repository;

    @Mock
    private FileStorageProvider storageProvider;

    private DocumentStorageService documentStorageService;

    @BeforeEach
    void setUp() {
        documentStorageService = new DocumentStorageService(repository, storageProvider);
    }

    @Nested
    @DisplayName("storeDocument() Tests")
    class StoreDocumentTests {

        @Test
        @DisplayName("Should store document successfully")
        void shouldStoreDocumentSuccessfully() throws FileStorageProvider.StorageException {
            // Given
            byte[] content = "Test PDF content".getBytes();
            String fileName = "invoice.pdf";
            String contentType = "application/pdf";
            DocumentType documentType = DocumentType.INVOICE_PDF;
            String invoiceId = "INV-001";
            String invoiceNumber = "INV-2024-001";

            FileStorageProvider.StorageResult storageResult =
                    new FileStorageProvider.StorageResult("/path/to/file.pdf", "http://example.com/file.pdf");

            when(storageProvider.store(any(), any())).thenReturn(storageResult);
            when(repository.save(any(StoredDocumentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            StoredDocument result = documentStorageService.storeDocument(
                    content, fileName, contentType, documentType, invoiceId, invoiceNumber
            );

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getFileName()).isEqualTo(fileName);
            assertThat(result.getContentType()).isEqualTo(contentType);
            assertThat(result.getStoragePath()).isEqualTo("/path/to/file.pdf");
            assertThat(result.getStorageUrl()).isEqualTo("http://example.com/file.pdf");
            assertThat(result.getFileSize()).isEqualTo(content.length);
            assertThat(result.getChecksum()).isNotNull();
            assertThat(result.getDocumentType()).isEqualTo(documentType);
            assertThat(result.getInvoiceId()).isEqualTo(invoiceId);
            assertThat(result.getInvoiceNumber()).isEqualTo(invoiceNumber);

            verify(storageProvider).store(content, fileName);
            verify(repository).save(any(StoredDocumentEntity.class));
        }

        @Test
        @DisplayName("Should calculate SHA-256 checksum")
        void shouldCalculateChecksum() throws FileStorageProvider.StorageException {
            // Given
            byte[] content = "Test content".getBytes();
            FileStorageProvider.StorageResult storageResult =
                    new FileStorageProvider.StorageResult("/path", "http://url");

            when(storageProvider.store(any(), any())).thenReturn(storageResult);
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            StoredDocument result = documentStorageService.storeDocument(
                    content, "test.txt", "text/plain", DocumentType.OTHER, null, null
            );

            // Then - SHA-256 hash of "Test content"
            assertThat(result.getChecksum()).isEqualTo("9d9595c5d94fb65b824f56e9999527dba9542481580d69feb89056aabaa0aa87");
        }

        @Test
        @DisplayName("Should use OTHER as default document type when null")
        void shouldUseOtherDefaultWhenNull() throws FileStorageProvider.StorageException {
            // Given
            byte[] content = "test".getBytes();
            FileStorageProvider.StorageResult storageResult =
                    new FileStorageProvider.StorageResult("/path", "http://url");

            when(storageProvider.store(any(), any())).thenReturn(storageResult);
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            StoredDocument result = documentStorageService.storeDocument(
                    content, "test.txt", "text/plain", null, null, null
            );

            // Then
            assertThat(result.getDocumentType()).isEqualTo(DocumentType.OTHER);
        }

        @Test
        @DisplayName("Should propagate storage exception")
        void shouldPropagateStorageException() throws FileStorageProvider.StorageException {
            // Given
            byte[] content = "test".getBytes();
            when(storageProvider.store(any(), any()))
                    .thenThrow(new FileStorageProvider.StorageException("Disk full"));

            // When & Then
            assertThatThrownBy(() -> documentStorageService.storeDocument(
                    content, "test.txt", "text/plain", DocumentType.OTHER, null, null
            ))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to store document")
                    .cause()
                    .isInstanceOf(FileStorageProvider.StorageException.class);
        }

        @Test
        @DisplayName("Should capture entity fields correctly")
        void shouldCaptureEntityFields() throws FileStorageProvider.StorageException {
            // Given
            byte[] content = "test".getBytes();
            FileStorageProvider.StorageResult storageResult =
                    new FileStorageProvider.StorageResult("/path/file.pdf", "http://url/file.pdf");

            when(storageProvider.store(any(), any())).thenReturn(storageResult);
            ArgumentCaptor<StoredDocumentEntity> entityCaptor = ArgumentCaptor.forClass(StoredDocumentEntity.class);
            when(repository.save(entityCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            documentStorageService.storeDocument(
                    content, "file.pdf", "application/pdf", DocumentType.INVOICE_PDF,
                    "INV-123", "INV-2024-123"
            );

            // Then
            StoredDocumentEntity capturedEntity = entityCaptor.getValue();
            assertThat(capturedEntity.getFileName()).isEqualTo("file.pdf");
            assertThat(capturedEntity.getContentType()).isEqualTo("application/pdf");
            assertThat(capturedEntity.getDocumentType()).isEqualTo(DocumentType.INVOICE_PDF);
            assertThat(capturedEntity.getInvoiceId()).isEqualTo("INV-123");
            assertThat(capturedEntity.getInvoiceNumber()).isEqualTo("INV-2024-123");
            assertThat(capturedEntity.getFileSize()).isPositive();
        }
    }

    @Nested
    @DisplayName("getDocument() Tests")
    class GetDocumentTests {

        @Test
        @DisplayName("Should retrieve document by ID")
        void shouldRetrieveDocumentById() {
            // Given
            String documentId = "doc-123";
            StoredDocumentEntity entity = StoredDocumentEntity.builder()
                    .id(documentId)
                    .fileName("test.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path/test.pdf")
                    .storageUrl("http://example.com/test.pdf")
                    .fileSize(1024L)
                    .checksum("abc123")
                    .documentType(DocumentType.INVOICE_PDF)
                    .invoiceId("INV-001")
                    .invoiceNumber("INV-2024-001")
                    .build();

            when(repository.findById(documentId)).thenReturn(Optional.of(entity));

            // When
            StoredDocument result = documentStorageService.getDocument(documentId);

            // Then
            assertThat(result.getId()).isEqualTo(documentId);
            assertThat(result.getFileName()).isEqualTo("test.pdf");
            assertThat(result.getInvoiceId()).isEqualTo("INV-001");
            verify(repository).findById(documentId);
        }

        @Test
        @DisplayName("Should throw exception when document not found")
        void shouldThrowWhenDocumentNotFound() {
            // Given
            String documentId = "non-existent";
            when(repository.findById(documentId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> documentStorageService.getDocument(documentId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Document not found");
        }
    }

    @Nested
    @DisplayName("getDocumentContent() Tests")
    class GetDocumentContentTests {

        @Test
        @DisplayName("Should retrieve and verify document content")
        void shouldRetrieveAndVerifyContent() throws FileStorageProvider.StorageException {
            // Given
            String documentId = "doc-123";
            byte[] originalContent = "PDF content".getBytes();
            String checksum = "7e7f04c8b5646f7ad29b1cb0c8085d4ff9c6b08f2a632f496641b31f524c7b98";

            StoredDocumentEntity entity = StoredDocumentEntity.builder()
                    .id(documentId)
                    .fileName("test.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path/test.pdf")
                    .storageUrl("http://example.com/test.pdf")
                    .fileSize((long) originalContent.length)
                    .checksum(checksum)
                    .documentType(DocumentType.INVOICE_PDF)
                    .build();

            when(repository.findById(documentId)).thenReturn(Optional.of(entity));
            when(storageProvider.retrieve(any())).thenReturn(originalContent);

            // When
            byte[] result = documentStorageService.getDocumentContent(documentId);

            // Then
            assertThat(result).isEqualTo(originalContent);
            verify(repository).findById(documentId);
            verify(storageProvider).retrieve("/path/test.pdf");
        }

        @Test
        @DisplayName("Should throw exception on checksum mismatch")
        void shouldThrowOnChecksumMismatch() throws FileStorageProvider.StorageException {
            // Given
            String documentId = "doc-123";
            byte[] differentContent = "Different content".getBytes();

            StoredDocumentEntity entity = StoredDocumentEntity.builder()
                    .id(documentId)
                    .fileName("test.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path/test.pdf")
                    .storageUrl("http://example.com/test.pdf")
                    .fileSize(100L)
                    .checksum("original-checksum")
                    .documentType(DocumentType.INVOICE_PDF)
                    .build();

            when(repository.findById(documentId)).thenReturn(Optional.of(entity));
            when(storageProvider.retrieve(any())).thenReturn(differentContent);

            // When & Then
            assertThatThrownBy(() -> documentStorageService.getDocumentContent(documentId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Document integrity check failed");
        }

        @Test
        @DisplayName("Should propagate storage exception during retrieval")
        void shouldPropagateStorageException() throws FileStorageProvider.StorageException {
            // Given
            String documentId = "doc-123";
            StoredDocumentEntity entity = StoredDocumentEntity.builder()
                    .id(documentId)
                    .fileName("test.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path/test.pdf")
                    .storageUrl("http://example.com/test.pdf")
                    .fileSize(100L)
                    .checksum("abc123")
                    .documentType(DocumentType.INVOICE_PDF)
                    .build();

            when(repository.findById(documentId)).thenReturn(Optional.of(entity));
            when(storageProvider.retrieve(any()))
                    .thenThrow(new FileStorageProvider.StorageException("File not found"));

            // When & Then
            assertThatThrownBy(() -> documentStorageService.getDocumentContent(documentId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to retrieve document");
        }
    }

    @Nested
    @DisplayName("deleteDocument() Tests")
    class DeleteDocumentTests {

        @Test
        @DisplayName("Should delete document successfully")
        void shouldDeleteDocument() throws FileStorageProvider.StorageException {
            // Given
            String documentId = "doc-123";
            StoredDocumentEntity entity = StoredDocumentEntity.builder()
                    .id(documentId)
                    .fileName("test.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path/test.pdf")
                    .storageUrl("http://example.com/test.pdf")
                    .fileSize(100L)
                    .checksum("abc123")
                    .documentType(DocumentType.INVOICE_PDF)
                    .build();

            when(repository.findById(documentId)).thenReturn(Optional.of(entity));
            doNothing().when(storageProvider).delete(any());

            // When
            documentStorageService.deleteDocument(documentId);

            // Then
            verify(storageProvider).delete("/path/test.pdf");
            verify(repository).deleteById(documentId);
        }

        @Test
        @DisplayName("Should propagate storage exception during deletion")
        void shouldPropagateStorageExceptionOnDelete() throws FileStorageProvider.StorageException {
            // Given
            String documentId = "doc-123";
            StoredDocumentEntity entity = StoredDocumentEntity.builder()
                    .id(documentId)
                    .fileName("test.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path/test.pdf")
                    .storageUrl("http://example.com/test.pdf")
                    .fileSize(100L)
                    .checksum("abc123")
                    .documentType(DocumentType.INVOICE_PDF)
                    .build();

            when(repository.findById(documentId)).thenReturn(Optional.of(entity));
            doThrow(new FileStorageProvider.StorageException("Delete failed"))
                    .when(storageProvider).delete(any());

            // When & Then
            assertThatThrownBy(() -> documentStorageService.deleteDocument(documentId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to delete document");
        }
    }

    @Nested
    @DisplayName("findByInvoiceId() Tests")
    class FindByInvoiceIdTests {

        @Test
        @DisplayName("Should find documents by invoice ID")
        void shouldFindByInvoiceId() {
            // Given
            String invoiceId = "INV-001";
            List<StoredDocumentEntity> entities = List.of(
                    createEntity("doc-1", invoiceId, "invoice.pdf"),
                    createEntity("doc-2", invoiceId, "attachment.pdf")
            );

            when(repository.findByInvoiceId(invoiceId)).thenReturn(entities);

            // When
            List<StoredDocument> result = documentStorageService.findByInvoiceId(invoiceId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo("doc-1");
            assertThat(result.get(1).getId()).isEqualTo("doc-2");
            assertThat(result).allMatch(doc -> invoiceId.equals(doc.getInvoiceId()));
            verify(repository).findByInvoiceId(invoiceId);
        }

        @Test
        @DisplayName("Should return empty list when no documents found")
        void shouldReturnEmptyListWhenNotFound() {
            // Given
            String invoiceId = "INV-NONEXISTENT";
            when(repository.findByInvoiceId(invoiceId)).thenReturn(List.of());

            // When
            List<StoredDocument> result = documentStorageService.findByInvoiceId(invoiceId);

            // Then
            assertThat(result).isEmpty();
        }

        private StoredDocumentEntity createEntity(String id, String invoiceId, String fileName) {
            return StoredDocumentEntity.builder()
                    .id(id)
                    .fileName(fileName)
                    .contentType("application/pdf")
                    .storagePath("/path/" + fileName)
                    .storageUrl("http://example.com/" + fileName)
                    .fileSize(100L)
                    .checksum("abc123")
                    .documentType(DocumentType.INVOICE_PDF)
                    .invoiceId(invoiceId)
                    .build();
        }
    }
}
