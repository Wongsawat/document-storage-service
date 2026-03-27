package com.wpanther.storage.application.usecase;

import com.wpanther.storage.domain.model.*;
import com.wpanther.storage.domain.repository.DocumentRepositoryPort;
import com.wpanther.storage.domain.exception.*;
import com.wpanther.storage.application.port.out.StorageProviderPort;
import com.wpanther.storage.application.port.out.MetricsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("FileStorageDomainService Tests")
@ExtendWith(MockitoExtension.class)
class FileStorageDomainServiceTest {

    @Mock
    private StorageProviderPort storageProvider;

    @Mock
    private DocumentRepositoryPort documentRepository;

    @Mock
    private MetricsPort metrics;

    @InjectMocks
    private FileStorageDomainService service;

    @BeforeEach
    void setUp() {
        lenient().when(metrics.timeStorageOperation()).thenReturn(() -> {});
    }

    @Nested
    @DisplayName("storeDocument()")
    class StoreDocumentTests {

        @Test
        @DisplayName("Should store document successfully")
        void shouldStoreDocumentSuccessfully() {
            // Given
            byte[] content = "test content".getBytes();
            StorageResult storageResult = StorageResult.success("/2024/03/04/uuid.pdf", "local");

            when(storageProvider.store(anyString(), any(), anyString(), anyLong()))
                .thenReturn(storageResult);
            when(documentRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

            // When
            StoredDocument result = service.storeDocument(content, "test.pdf",
                                                          DocumentType.INVOICE_PDF, "INV-001");

            // Then
            assertThat(result.getDocumentType()).isEqualTo(DocumentType.INVOICE_PDF);
            assertThat(result.getInvoiceId()).isEqualTo("INV-001");
            assertThat(result.getStoragePath()).isEqualTo("/2024/03/04/uuid.pdf");
            assertThat(result.getFileName()).isEqualTo("test.pdf");
            assertThat(result.getFileSize()).isEqualTo(content.length);
            assertThat(result.getChecksum()).isNotNull();
            assertThat(result.getCreatedAt()).isNotNull();

            verify(storageProvider).store(anyString(), any(), eq("test.pdf"), eq((long) content.length));
            verify(documentRepository).save(any(StoredDocument.class));
        }

        @Test
        @DisplayName("Should throw exception when content is null")
        void shouldThrowExceptionWhenContentIsNull() {
            // When/Then
            assertThatThrownBy(() -> service.storeDocument(null, "test.pdf",
                                                           DocumentType.INVOICE_PDF, "INV-001"))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("cannot be empty");

            verify(storageProvider, never()).store(any(), any(), any(), anyLong());
            verify(documentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when content is empty")
        void shouldThrowExceptionWhenContentIsEmpty() {
            // Given
            byte[] empty = new byte[0];

            // When/Then
            assertThatThrownBy(() -> service.storeDocument(empty, "test.pdf",
                                                           DocumentType.INVOICE_PDF, "INV-001"))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("cannot be empty");

            verify(storageProvider, never()).store(any(), any(), any(), anyLong());
            verify(documentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should use provided contentType when specified")
        void shouldUseProvidedContentType() {
            // Given
            byte[] content = "test content".getBytes();
            StorageResult storageResult = StorageResult.success("/path/file.pdf", "local");

            when(storageProvider.store(anyString(), any(), anyString(), anyLong()))
                .thenReturn(storageResult);
            when(documentRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

            // When
            StoredDocument result = service.storeDocument(content, "test.pdf", "application/json",
                                                          DocumentType.OTHER, "INV-001", "INV-2024-001");

            // Then
            assertThat(result.getContentType()).isEqualTo("application/json");
            assertThat(result.getInvoiceNumber()).isEqualTo("INV-2024-001");
        }
    }

    @Nested
    @DisplayName("getDocument()")
    class GetDocumentTests {

        @Test
        @DisplayName("Should return document when found")
        void shouldReturnDocumentWhenFound() {
            // Given
            String documentId = "doc-123";
            StoredDocument doc = StoredDocument.builder()
                .id(documentId)
                .invoiceId("INV-001")
                .documentType(DocumentType.INVOICE_PDF)
                .fileName("test.pdf")
                .contentType("application/pdf")
                .storagePath("/path/to/doc")
                .storageUrl("http://example.com/doc")
                .fileSize((long)1024)
                .checksum("abc123")
                .createdAt(LocalDateTime.now())
                .build();
            when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));

            // When
            Optional<StoredDocument> result = service.getDocument(documentId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(documentId);
            assertThat(result.get().getInvoiceId()).isEqualTo("INV-001");
        }

        @Test
        @DisplayName("Should return empty when document not found")
        void shouldReturnEmptyWhenNotFound() {
            // Given
            when(documentRepository.findById("doc-999")).thenReturn(Optional.empty());

            // When
            Optional<StoredDocument> result = service.getDocument("doc-999");

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getDocumentsByInvoice()")
    class GetDocumentsByInvoiceTests {

        @Test
        @DisplayName("Should return list of documents")
        void shouldReturnListOfDocuments() {
            // Given
            String invoiceId = "INV-001";
            List<StoredDocument> docs = List.of(
                StoredDocument.builder()
                    .id("doc-1")
                    .invoiceId(invoiceId)
                    .documentType(DocumentType.INVOICE_PDF)
                    .fileName("invoice.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path/doc1")
                    .storageUrl("http://example.com/doc1")
                    .fileSize((long)1024)
                    .checksum("abc1")
                    .build(),
                StoredDocument.builder()
                    .id("doc-2")
                    .invoiceId(invoiceId)
                    .documentType(DocumentType.SIGNED_XML)
                    .fileName("signed.xml")
                    .contentType("application/xml")
                    .storagePath("/path/doc2")
                    .storageUrl("http://example.com/doc2")
                    .fileSize((long)512)
                    .checksum("abc2")
                    .build()
            );
            when(documentRepository.findByInvoiceId(invoiceId)).thenReturn(docs);

            // When
            List<StoredDocument> result = service.getDocumentsByInvoice(invoiceId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getInvoiceId()).isEqualTo(invoiceId);
            assertThat(result.get(1).getInvoiceId()).isEqualTo(invoiceId);
        }

        @Test
        @DisplayName("Should return empty list when no documents found")
        void shouldReturnEmptyList() {
            // Given
            when(documentRepository.findByInvoiceId("INV-999")).thenReturn(List.of());

            // When
            List<StoredDocument> result = service.getDocumentsByInvoice("INV-999");

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteDocument()")
    class DeleteDocumentTests {

        @Test
        @DisplayName("Should delete document successfully")
        void shouldDeleteDocumentSuccessfully() {
            // Given
            String documentId = "doc-123";
            String storagePath = "/path/to/doc";
            StoredDocument doc = StoredDocument.builder()
                .id(documentId)
                .fileName("test.pdf")
                .contentType("application/pdf")
                .storagePath(storagePath)
                .storageUrl("http://example.com/doc")
                .fileSize((long)1024)
                .checksum("abc123")
                .build();
            when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));
            doNothing().when(storageProvider).delete(storagePath);
            doNothing().when(documentRepository).deleteById(documentId);

            // When
            service.deleteDocument(documentId);

            // Then
            verify(storageProvider).delete(storagePath);
            verify(documentRepository).deleteById(documentId);
        }

        @Test
        @DisplayName("Should throw exception when document not found")
        void shouldThrowExceptionWhenNotFound() {
            // Given
            when(documentRepository.findById("doc-999")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.deleteDocument("doc-999"))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining("doc-999");

            verify(storageProvider, never()).delete(anyString());
            verify(documentRepository, never()).deleteById(anyString());
        }
    }

    @Nested
    @DisplayName("existsByInvoiceAndType()")
    class ExistsByInvoiceAndTypeTests {

        @Test
        @DisplayName("Should return true when document exists")
        void shouldReturnTrueWhenExists() {
            // Given
            when(documentRepository.existsByInvoiceIdAndDocumentType("INV-001", DocumentType.INVOICE_PDF))
                .thenReturn(true);

            // When
            boolean result = service.existsByInvoiceAndType("INV-001", DocumentType.INVOICE_PDF);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when document does not exist")
        void shouldReturnFalseWhenNotExists() {
            // Given
            when(documentRepository.existsByInvoiceIdAndDocumentType("INV-001", DocumentType.INVOICE_PDF))
                .thenReturn(false);

            // When
            boolean result = service.existsByInvoiceAndType("INV-001", DocumentType.INVOICE_PDF);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("getDocumentContent()")
    class GetDocumentContentTests {

        @Test
        @DisplayName("Should return document content")
        void shouldReturnDocumentContent() throws Exception {
            // Given
            String documentId = "doc-123";
            String storagePath = "/path/to/doc";
            byte[] expectedContent = "test content".getBytes();
            StoredDocument doc = StoredDocument.builder()
                .id(documentId)
                .fileName("test.pdf")
                .contentType("application/pdf")
                .storagePath(storagePath)
                .storageUrl("http://example.com/doc")
                .fileSize((long)expectedContent.length)
                .checksum("abc123")
                .build();

            when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));
            when(storageProvider.retrieve(storagePath))
                .thenReturn(new ByteArrayInputStream(expectedContent));

            // When
            byte[] result = service.getDocumentContent(documentId);

            // Then
            assertThat(result).isEqualTo(expectedContent);
            verify(storageProvider).retrieve(storagePath);
        }

        @Test
        @DisplayName("Should throw exception when document not found")
        void shouldThrowExceptionWhenDocumentNotFound() {
            // Given
            when(documentRepository.findById("doc-999")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.getDocumentContent("doc-999"))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining("doc-999");

            verify(storageProvider, never()).retrieve(anyString());
        }

        @Test
        @DisplayName("Should throw exception when storage retrieval fails")
        void shouldThrowExceptionWhenStorageFails() throws Exception {
            // Given
            String documentId = "doc-123";
            String storagePath = "/path/to/doc";
            StoredDocument doc = StoredDocument.builder()
                .id(documentId)
                .fileName("test.pdf")
                .contentType("application/pdf")
                .storagePath(storagePath)
                .storageUrl("http://example.com/doc")
                .fileSize((long)1024)
                .checksum("abc123")
                .build();

            when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));
            when(storageProvider.retrieve(storagePath))
                .thenThrow(new RuntimeException("Storage error"));

            // When/Then
            assertThatThrownBy(() -> service.getDocumentContent(documentId))
                .isInstanceOf(StorageFailedException.class)
                .hasMessageContaining("Failed to retrieve document content");
        }
    }

    @Nested
    @DisplayName("downloadContent()")
    class DownloadContentTests {

        @Test
        @DisplayName("Should download content from storage")
        void shouldDownloadContent() throws Exception {
            // Given
            String storagePath = "/path/to/file";
            byte[] expectedContent = "test content".getBytes();

            when(storageProvider.retrieve(storagePath))
                .thenReturn(new ByteArrayInputStream(expectedContent));

            // When
            InputStream result = service.downloadContent(storagePath);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.readAllBytes()).isEqualTo(expectedContent);
            verify(storageProvider).retrieve(storagePath);
        }
    }
}
