package com.wpanther.storage.domain.service;

import com.wpanther.storage.domain.model.*;
import com.wpanther.storage.domain.port.outbound.*;
import com.wpanther.storage.domain.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileStorageDomainServiceTest {

    @Mock
    private StorageProviderPort storageProvider;

    @Mock
    private DocumentRepositoryPort documentRepository;

    @InjectMocks
    private FileStorageDomainService service;

    @Test
    void storeDocument_success() {
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

        verify(storageProvider).store(anyString(), any(), eq("test.pdf"), eq((long) content.length));
        verify(documentRepository).save(any(StoredDocument.class));
    }

    @Test
    void storeDocument_emptyContent_throwsException() {
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
    void getDocument_found_returnsDocument() {
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
            .fileSize(1024)
            .checksum("abc123")
            .build();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));

        // When
        Optional<StoredDocument> result = service.getDocument(documentId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(documentId);
    }

    @Test
    void deleteDocument_success() {
        // Given
        String documentId = "doc-123";
        StoredDocument doc = StoredDocument.builder()
            .id(documentId)
            .fileName("test.pdf")
            .contentType("application/pdf")
            .storagePath("/path/to/doc")
            .storageUrl("http://example.com/doc")
            .fileSize(1024)
            .checksum("abc123")
            .build();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));
        doNothing().when(storageProvider).delete("/path/to/doc");
        doNothing().when(documentRepository).deleteById(documentId);

        // When
        service.deleteDocument(documentId);

        // Then
        verify(storageProvider).delete("/path/to/doc");
        verify(documentRepository).deleteById(documentId);
    }

    @Test
    void deleteDocument_notFound_throwsException() {
        // Given
        when(documentRepository.findById("doc-999")).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> service.deleteDocument("doc-999"))
            .isInstanceOf(DocumentNotFoundException.class);

        verify(storageProvider, never()).delete(anyString());
        verify(documentRepository, never()).deleteById(anyString());
    }

    @Test
    void getDocumentsByInvoice_returnsList() {
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
                .fileSize(1024)
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
                .fileSize(512)
                .checksum("abc2")
                .build()
        );
        when(documentRepository.findByInvoiceId(invoiceId)).thenReturn(docs);

        // When
        List<StoredDocument> result = service.getDocumentsByInvoice(invoiceId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getInvoiceId()).isEqualTo(invoiceId);
    }

    @Test
    void existsByInvoiceAndType_returnsTrue() {
        // Given
        when(documentRepository.existsByInvoiceIdAndDocumentType("INV-001", DocumentType.INVOICE_PDF))
            .thenReturn(true);

        // When
        boolean result = service.existsByInvoiceAndType("INV-001", DocumentType.INVOICE_PDF);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void existsByInvoiceAndType_returnsFalse() {
        // Given
        when(documentRepository.existsByInvoiceIdAndDocumentType("INV-001", DocumentType.INVOICE_PDF))
            .thenReturn(false);

        // When
        boolean result = service.existsByInvoiceAndType("INV-001", DocumentType.INVOICE_PDF);

        // Then
        assertThat(result).isFalse();
    }
}
