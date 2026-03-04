package com.wpanther.storage.infrastructure.adapter.inbound.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.storage.domain.port.inbound.DocumentStorageUseCase;
import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("DocumentStorageController Tests")
class DocumentStorageControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private DocumentStorageUseCase documentStorageUseCase;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();

        DocumentStorageController controller = new DocumentStorageController(documentStorageUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Nested
    @DisplayName("uploadDocument() Tests")
    class UploadDocumentTests {

        @Test
        @DisplayName("Should upload document successfully")
        void shouldUploadDocumentSuccessfully() throws Exception {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "invoice.pdf",
                    "application/pdf",
                    "PDF content".getBytes()
            );

            StoredDocument storedDocument = StoredDocument.builder()
                    .id("doc-123")
                    .fileName("invoice.pdf")
                    .contentType("application/pdf")
                    .storagePath("/storage/abc123.pdf")
                    .storageUrl("http://localhost:8084/documents/abc123.pdf")
                    .fileSize(11L)
                    .checksum("abc123")
                    .build();

            when(documentStorageUseCase.storeDocument(
                    any(byte[].class), eq("invoice.pdf"), eq("application/pdf"),
                    eq(DocumentType.OTHER), isNull(), isNull()
            )).thenReturn(storedDocument);

            // When & Then
            mockMvc.perform(multipart("/api/v1/documents")
                            .file(file)
                            .param("documentType", "OTHER"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.documentId").value("doc-123"))
                    .andExpect(jsonPath("$.fileName").value("invoice.pdf"))
                    .andExpect(jsonPath("$.url").value("http://localhost:8084/documents/abc123.pdf"))
                    .andExpect(jsonPath("$.fileSize").value(11))
                    .andExpect(jsonPath("$.checksum").value("abc123"));
        }

        @Test
        @DisplayName("Should upload document with invoice metadata")
        void shouldUploadDocumentWithMetadata() throws Exception {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "invoice.pdf",
                    "application/pdf",
                    "PDF content".getBytes()
            );

            StoredDocument storedDocument = StoredDocument.builder()
                    .id("doc-456")
                    .fileName("invoice.pdf")
                    .contentType("application/pdf")
                    .storagePath("/storage/def456.pdf")
                    .storageUrl("http://localhost:8084/documents/def456.pdf")
                    .fileSize(11L)
                    .checksum("hash123")
                    .build();

            when(documentStorageUseCase.storeDocument(
                    any(byte[].class), eq("invoice.pdf"), eq("application/pdf"),
                    eq(DocumentType.INVOICE_PDF), eq("INV-001"), eq("INV-2024-001")
            )).thenReturn(storedDocument);

            // When & Then
            mockMvc.perform(multipart("/api/v1/documents")
                            .file(file)
                            .param("documentType", "INVOICE_PDF")
                            .param("invoiceId", "INV-001")
                            .param("invoiceNumber", "INV-2024-001"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.documentId").value("doc-456"));
        }

        @Test
        @DisplayName("Should return 500 when storage fails")
        void shouldReturn500WhenStorageFails() throws Exception {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "error.pdf",
                    "application/pdf",
                    "Error content".getBytes()
            );

            when(documentStorageUseCase.storeDocument(
                    any(byte[].class), anyString(), anyString(),
                    any(DocumentType.class), any(), any()
            )).thenThrow(new RuntimeException("Storage failure"));

            // When & Then
            mockMvc.perform(multipart("/api/v1/documents")
                            .file(file)
                            .param("documentType", "OTHER"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("Failed to upload document"))
                    .andExpect(jsonPath("$.message").value("Storage failure"));
        }
    }

    @Nested
    @DisplayName("downloadDocument() Tests")
    class DownloadDocumentTests {

        @Test
        @DisplayName("Should download document successfully")
        void shouldDownloadDocumentSuccessfully() throws Exception {
            // Given
            String documentId = "doc-123";
            byte[] content = "PDF content here".getBytes();

            StoredDocument document = StoredDocument.builder()
                    .id(documentId)
                    .fileName("invoice.pdf")
                    .contentType("application/pdf")
                    .storagePath("/storage/doc-123.pdf")
                    .storageUrl("http://localhost:8084/documents/doc-123")
                    .fileSize(15L)
                    .checksum("hash123")
                    .build();

            when(documentStorageUseCase.getDocument(documentId)).thenReturn(Optional.of(document));
            when(documentStorageUseCase.getDocumentContent(documentId)).thenReturn(content);

            // When & Then
            mockMvc.perform(get("/api/v1/documents/{id}", documentId))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Content-Disposition"))
                    .andExpect(content().contentType("application/pdf"))
                    .andExpect(content().bytes(content));
        }

        @Test
        @DisplayName("Should return 404 when document not found")
        void shouldReturn404WhenNotFound() throws Exception {
            // Given
            String documentId = "non-existent";
            when(documentStorageUseCase.getDocument(documentId))
                    .thenThrow(new IllegalArgumentException("Document not found"));

            // When & Then
            mockMvc.perform(get("/api/v1/documents/{id}", documentId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 500 on download error")
        void shouldReturn500OnError() throws Exception {
            // Given
            String documentId = "doc-error";
            when(documentStorageUseCase.getDocument(documentId)).thenReturn(Optional.of(
                    StoredDocument.builder()
                            .id(documentId)
                            .fileName("error.pdf")
                            .contentType("application/pdf")
                            .storagePath("/path")
                            .storageUrl("http://url")
                            .fileSize(100L)
                            .checksum("hash")
                            .build()
            ));
            when(documentStorageUseCase.getDocumentContent(documentId))
                    .thenThrow(new RuntimeException("Read error"));

            // When & Then
            mockMvc.perform(get("/api/v1/documents/{id}", documentId))
                    .andExpect(status().isInternalServerError());
        }
    }

    @Nested
    @DisplayName("getDocumentMetadata() Tests")
    class GetDocumentMetadataTests {

        @Test
        @DisplayName("Should return document metadata")
        void shouldReturnMetadata() throws Exception {
            // Given
            String documentId = "doc-123";
            StoredDocument document = StoredDocument.builder()
                    .id(documentId)
                    .fileName("invoice.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path/doc-123")
                    .storageUrl("http://localhost:8084/documents/doc-123")
                    .fileSize(1024L)
                    .checksum("abc123")
                    .documentType(DocumentType.INVOICE_PDF)
                    .createdAt(LocalDateTime.of(2024, 1, 1, 10, 0))
                    .invoiceId("INV-001")
                    .invoiceNumber("INV-2024-001")
                    .build();

            when(documentStorageUseCase.getDocument(documentId)).thenReturn(Optional.of(document));

            // When & Then
            mockMvc.perform(get("/api/v1/documents/{id}/metadata", documentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(documentId))
                    .andExpect(jsonPath("$.fileName").value("invoice.pdf"))
                    .andExpect(jsonPath("$.contentType").value("application/pdf"))
                    .andExpect(jsonPath("$.fileSize").value(1024))
                    .andExpect(jsonPath("$.checksum").value("abc123"))
                    .andExpect(jsonPath("$.documentType").value("INVOICE_PDF"))
                    .andExpect(jsonPath("$.invoiceId").value("INV-001"))
                    .andExpect(jsonPath("$.invoiceNumber").value("INV-2024-001"));
        }

        @Test
        @DisplayName("Should return 404 when metadata not found")
        void shouldReturn404WhenMetadataNotFound() throws Exception {
            // Given
            when(documentStorageUseCase.getDocument("non-existent"))
                    .thenThrow(new IllegalArgumentException("Document not found"));

            // When & Then
            mockMvc.perform(get("/api/v1/documents/{id}/metadata", "non-existent"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should handle null invoice fields")
        void shouldHandleNullInvoiceFields() throws Exception {
            // Given
            StoredDocument document = StoredDocument.builder()
                    .id("doc-123")
                    .fileName("other.pdf")
                    .contentType("application/pdf")
                    .storagePath("/path")
                    .storageUrl("http://url")
                    .fileSize(100L)
                    .checksum("hash")
                    .documentType(DocumentType.OTHER)
                    .invoiceId(null)
                    .invoiceNumber(null)
                    .build();

            when(documentStorageUseCase.getDocument("doc-123")).thenReturn(Optional.of(document));

            // When & Then
            mockMvc.perform(get("/api/v1/documents/{id}/metadata", "doc-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.invoiceId").value(""))
                    .andExpect(jsonPath("$.invoiceNumber").value(""));
        }
    }

    @Nested
    @DisplayName("deleteDocument() Tests")
    class DeleteDocumentTests {

        @Test
        @DisplayName("Should delete document successfully")
        void shouldDeleteSuccessfully() throws Exception {
            // Given
            String documentId = "doc-123";
            when(documentStorageUseCase.getDocument(documentId)).thenReturn(Optional.of(
                    StoredDocument.builder()
                            .id(documentId)
                            .fileName("delete.pdf")
                            .contentType("application/pdf")
                            .storagePath("/path")
                            .storageUrl("http://url")
                            .fileSize(100L)
                            .checksum("hash")
                            .build()
            ));
            doNothing().when(documentStorageUseCase).deleteDocument(documentId);

            // When & Then
            mockMvc.perform(delete("/api/v1/documents/{id}", documentId))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Should return 404 when deleting non-existent document")
        void shouldReturn404WhenDeletingNonExistent() throws Exception {
            // Given
            doThrow(new IllegalArgumentException("Document not found"))
                    .when(documentStorageUseCase).deleteDocument("non-existent");

            // When & Then
            mockMvc.perform(delete("/api/v1/documents/{id}", "non-existent"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 500 on delete error")
        void shouldReturn500OnDeleteError() throws Exception {
            // Given
            String documentId = "doc-error";
            when(documentStorageUseCase.getDocument(documentId)).thenReturn(Optional.of(
                    StoredDocument.builder()
                            .id(documentId)
                            .fileName("error.pdf")
                            .contentType("application/pdf")
                            .storagePath("/path")
                            .storageUrl("http://url")
                            .fileSize(100L)
                            .checksum("hash")
                            .build()
            ));
            doThrow(new RuntimeException("Delete failed"))
                    .when(documentStorageUseCase).deleteDocument(documentId);

            // When & Then
            mockMvc.perform(delete("/api/v1/documents/{id}", documentId))
                    .andExpect(status().isInternalServerError());
        }
    }

    @Nested
    @DisplayName("getDocumentsByInvoiceId() Tests")
    class GetByInvoiceIdTests {

        @Test
        @DisplayName("Should return documents for invoice")
        void shouldReturnDocuments() throws Exception {
            // Given
            String invoiceId = "INV-001";
            List<StoredDocument> documents = List.of(
                    StoredDocument.builder()
                            .id("doc-1")
                            .fileName("invoice.pdf")
                            .contentType("application/pdf")
                            .storagePath("/storage/doc-1.pdf")
                            .storageUrl("http://localhost:8084/documents/doc-1")
                            .fileSize(100L)
                            .checksum("hash1")
                            .documentType(DocumentType.INVOICE_PDF)
                            .createdAt(LocalDateTime.of(2024, 1, 1, 10, 0))
                            .build(),
                    StoredDocument.builder()
                            .id("doc-2")
                            .fileName("attachment.pdf")
                            .contentType("application/pdf")
                            .storagePath("/storage/doc-2.pdf")
                            .storageUrl("http://localhost:8084/documents/doc-2")
                            .fileSize(50L)
                            .checksum("hash2")
                            .documentType(DocumentType.ATTACHMENT)
                            .createdAt(LocalDateTime.of(2024, 1, 1, 11, 0))
                            .build()
            );

            when(documentStorageUseCase.getDocumentsByInvoice(invoiceId)).thenReturn(documents);

            // When & Then
            mockMvc.perform(get("/api/v1/documents/invoice/{invoiceId}", invoiceId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value("doc-1"))
                    .andExpect(jsonPath("$[0].fileName").value("invoice.pdf"))
                    .andExpect(jsonPath("$[0].url").value("http://localhost:8084/documents/doc-1"))
                    .andExpect(jsonPath("$[1].id").value("doc-2"));
        }

        @Test
        @DisplayName("Should return empty list when no documents found")
        void shouldReturnEmptyList() throws Exception {
            // Given
            when(documentStorageUseCase.getDocumentsByInvoice("INV-EMPTY")).thenReturn(List.of());

            // When & Then
            mockMvc.perform(get("/api/v1/documents/invoice/{invoiceId}", "INV-EMPTY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }
}
