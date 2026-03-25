package com.wpanther.storage.application.usecase;

import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.storage.application.dto.event.*;
import com.wpanther.storage.domain.exception.InvalidDocumentException;
import com.wpanther.storage.domain.exception.StorageFailedException;
import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.application.port.out.MessagePublisherPort;
import com.wpanther.storage.application.port.out.PdfDownloadPort;
import com.wpanther.storage.application.usecase.SagaCommandUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("SagaOrchestrationService Tests")
@ExtendWith(MockitoExtension.class)
class SagaOrchestrationServiceTest {

    @Mock
    private FileStorageDomainService storageService;

    @Mock
    private PdfDownloadPort pdfDownloadPort;

    @Mock
    private MessagePublisherPort messagePublisher;

    @InjectMocks
    private SagaOrchestrationService service;

    @Nested
    @DisplayName("Constructor and interface")
    class BasicTests {

        @Test
        @DisplayName("Should create service successfully")
        void shouldCreateServiceSuccessfully() {
            assertNotNull(service);
        }

        @Test
        @DisplayName("Should implement SagaCommandUseCase")
        void shouldImplementSagaCommandUseCase() {
            assertTrue(service instanceof SagaCommandUseCase);
        }
    }

    @Nested
    @DisplayName("handleProcessCommand(ProcessDocumentStorageCommand)")
    class ProcessDocumentStorageTests {

        @Test
        @DisplayName("Should process new document successfully")
        void shouldProcessNewDocumentSuccessfully() {
            String documentId = "doc-123";
            byte[] pdfContent = "PDF content".getBytes();
            StoredDocument storedDoc = StoredDocument.builder()
                .id("stored-123")
                .invoiceId(documentId)
                .fileName("doc-123.pdf")
                .contentType("application/pdf")
                .storagePath("/2024/03/05/stored-123.pdf")
                .storageUrl("http://localhost:8084/api/v1/documents/stored-123")
                .fileSize(pdfContent.length)
                .checksum("abc123")
                .documentType(DocumentType.INVOICE_PDF)
                .build();

            ProcessDocumentStorageCommand command = new ProcessDocumentStorageCommand(
                "saga-123", SagaStep.STORE_DOCUMENT, "corr-123",
                documentId, "INV-2024-001", "INVOICE_PDF",
                "http://minio:9000/file.pdf", "signed-doc-123", "PAdES-BASELINE-T"
            );

            when(storageService.existsByInvoiceAndType(documentId, DocumentType.INVOICE_PDF)).thenReturn(false);
            when(pdfDownloadPort.downloadPdf(command.getSignedPdfUrl())).thenReturn(pdfContent);
            when(storageService.storeDocument(eq(pdfContent), anyString(), eq(DocumentType.INVOICE_PDF), eq(documentId)))
                .thenReturn(storedDoc);
            doNothing().when(messagePublisher).publishEvent(any(DocumentStoredEvent.class));
            doNothing().when(messagePublisher).publishReply(any(DocumentStorageReplyEvent.class));

            service.handleProcessCommand(command);

            verify(storageService).storeDocument(eq(pdfContent), anyString(), eq(DocumentType.INVOICE_PDF), eq(documentId));
            verify(messagePublisher).publishEvent(any(DocumentStoredEvent.class));
            ArgumentCaptor<DocumentStorageReplyEvent> replyCaptor = ArgumentCaptor.forClass(DocumentStorageReplyEvent.class);
            verify(messagePublisher).publishReply(replyCaptor.capture());
            assertEquals("saga-123", replyCaptor.getValue().getSagaId());
        }

        @Test
        @DisplayName("Should skip processing when document already exists (idempotency)")
        void shouldSkipWhenDocumentAlreadyExists() {
            String documentId = "doc-123";
            ProcessDocumentStorageCommand command = new ProcessDocumentStorageCommand(
                "saga-123", SagaStep.STORE_DOCUMENT, "corr-123",
                documentId, "INV-2024-001", "INVOICE_PDF",
                "http://minio:9000/file.pdf", "signed-doc-123", "PAdES-BASELINE-T"
            );

            when(storageService.existsByInvoiceAndType(documentId, DocumentType.INVOICE_PDF)).thenReturn(true);

            service.handleProcessCommand(command);

            verify(pdfDownloadPort, never()).downloadPdf(anyString());
            verify(storageService, never()).storeDocument(any(), anyString(), any(), anyString());
            ArgumentCaptor<DocumentStorageReplyEvent> replyCaptor = ArgumentCaptor.forClass(DocumentStorageReplyEvent.class);
            verify(messagePublisher).publishReply(replyCaptor.capture());
            assertEquals("saga-123", replyCaptor.getValue().getSagaId());
        }

        @Test
        @DisplayName("Should publish failure reply when download fails")
        void shouldPublishFailureWhenDownloadFails() {
            String documentId = "doc-123";
            ProcessDocumentStorageCommand command = new ProcessDocumentStorageCommand(
                "saga-123", SagaStep.STORE_DOCUMENT, "corr-123",
                documentId, "INV-2024-001", "INVOICE_PDF",
                "http://minio:9000/file.pdf", "signed-doc-123", "PAdES-BASELINE-T"
            );

            when(storageService.existsByInvoiceAndType(documentId, DocumentType.INVOICE_PDF)).thenReturn(false);
            when(pdfDownloadPort.downloadPdf(command.getSignedPdfUrl()))
                .thenThrow(new StorageFailedException("Download failed"));

            service.handleProcessCommand(command);

            ArgumentCaptor<DocumentStorageReplyEvent> replyCaptor = ArgumentCaptor.forClass(DocumentStorageReplyEvent.class);
            verify(messagePublisher).publishReply(replyCaptor.capture());
            assertEquals("saga-123", replyCaptor.getValue().getSagaId());
            assertNotNull(replyCaptor.getValue().getErrorMessage());
        }
    }

    @Nested
    @DisplayName("handleProcessCommand(ProcessSignedXmlStorageCommand)")
    class ProcessSignedXmlStorageTests {

        @Test
        @DisplayName("Should process signed XML successfully")
        void shouldProcessSignedXmlSuccessfully() {
            String documentId = "doc-456";
            String xmlContent = "<?xml version=\"1.0\"?><root>signed</root>";
            byte[] xmlBytes = xmlContent.getBytes();
            StoredDocument storedDoc = StoredDocument.builder()
                .id("stored-456")
                .invoiceId(documentId)
                .fileName("INV_INV-2024-001_signed.xml")
                .contentType("application/xml")
                .storagePath("/2024/03/05/stored-456.xml")
                .storageUrl("http://localhost:8084/api/v1/documents/stored-456")
                .fileSize(xmlBytes.length)
                .checksum("xyz789")
                .documentType(DocumentType.SIGNED_XML)
                .build();

            ProcessSignedXmlStorageCommand command = new ProcessSignedXmlStorageCommand(
                "saga-456", SagaStep.SIGNEDXML_STORAGE, "corr-456",
                documentId, "INV-2024-001", "INVOICE",
                "http://minio:9000/signed.xml", "XAdES-BASELINE-T"
            );

            when(storageService.existsByInvoiceAndType(documentId, DocumentType.SIGNED_XML)).thenReturn(false);
            when(pdfDownloadPort.downloadContent(command.getSignedXmlUrl())).thenReturn(xmlContent);
            when(storageService.storeDocument(eq(xmlBytes), anyString(), eq(DocumentType.SIGNED_XML), eq(documentId)))
                .thenReturn(storedDoc);
            doNothing().when(messagePublisher).publishReply(any(SignedXmlStorageReplyEvent.class));

            service.handleProcessCommand(command);

            verify(storageService).storeDocument(eq(xmlBytes), anyString(), eq(DocumentType.SIGNED_XML), eq(documentId));
            ArgumentCaptor<SignedXmlStorageReplyEvent> replyCaptor = ArgumentCaptor.forClass(SignedXmlStorageReplyEvent.class);
            verify(messagePublisher).publishReply(replyCaptor.capture());
            assertEquals("saga-456", replyCaptor.getValue().getSagaId());
        }

        @Test
        @DisplayName("Should skip when signed XML already exists")
        void shouldSkipWhenSignedXmlAlreadyExists() {
            String documentId = "doc-456";
            ProcessSignedXmlStorageCommand command = new ProcessSignedXmlStorageCommand(
                "saga-456", SagaStep.SIGNEDXML_STORAGE, "corr-456",
                documentId, "INV-2024-001", "INVOICE",
                "http://minio:9000/signed.xml", "XAdES-BASELINE-T"
            );

            when(storageService.existsByInvoiceAndType(documentId, DocumentType.SIGNED_XML)).thenReturn(true);

            service.handleProcessCommand(command);

            verify(pdfDownloadPort, never()).downloadContent(anyString());
            ArgumentCaptor<SignedXmlStorageReplyEvent> replyCaptor = ArgumentCaptor.forClass(SignedXmlStorageReplyEvent.class);
            verify(messagePublisher).publishReply(replyCaptor.capture());
            assertEquals("saga-456", replyCaptor.getValue().getSagaId());
        }

        @Test
        @DisplayName("Should publish failure when XML content is blank")
        void shouldPublishFailureWhenXmlContentIsBlank() {
            String documentId = "doc-456";
            ProcessSignedXmlStorageCommand command = new ProcessSignedXmlStorageCommand(
                "saga-456", SagaStep.SIGNEDXML_STORAGE, "corr-456",
                documentId, "INV-2024-001", "INVOICE",
                "http://minio:9000/signed.xml", "XAdES-BASELINE-T"
            );

            when(storageService.existsByInvoiceAndType(documentId, DocumentType.SIGNED_XML)).thenReturn(false);
            when(pdfDownloadPort.downloadContent(command.getSignedXmlUrl())).thenReturn("");

            service.handleProcessCommand(command);

            ArgumentCaptor<SignedXmlStorageReplyEvent> replyCaptor = ArgumentCaptor.forClass(SignedXmlStorageReplyEvent.class);
            verify(messagePublisher).publishReply(replyCaptor.capture());
            assertNotNull(replyCaptor.getValue().getErrorMessage());
        }
    }

    @Nested
    @DisplayName("handleProcessCommand(ProcessPdfStorageCommand)")
    class ProcessPdfStorageTests {

        @Test
        @DisplayName("Should process PDF storage successfully")
        void shouldProcessPdfStorageSuccessfully() {
            String documentId = "doc-789";
            byte[] pdfContent = "PDF content".getBytes();
            StoredDocument storedDoc = StoredDocument.builder()
                .id("stored-789")
                .invoiceId(documentId)
                .fileName("doc-789_unsigned.pdf")
                .contentType("application/pdf")
                .storagePath("/2024/03/05/stored-789.pdf")
                .storageUrl("http://localhost:8084/api/v1/documents/stored-789")
                .fileSize(pdfContent.length)
                .checksum("def456")
                .documentType(DocumentType.UNSIGNED_PDF)
                .build();

            ProcessPdfStorageCommand command = new ProcessPdfStorageCommand(
                "saga-789", SagaStep.PDF_STORAGE, "corr-789",
                documentId, "INV-2024-001", "INVOICE_PDF",
                "http://minio:9000/unsigned.pdf", (long) pdfContent.length
            );

            when(storageService.existsByInvoiceAndType(documentId, DocumentType.UNSIGNED_PDF)).thenReturn(false);
            when(pdfDownloadPort.downloadPdf(command.getPdfUrl())).thenReturn(pdfContent);
            when(storageService.storeDocument(eq(pdfContent), anyString(), eq(DocumentType.UNSIGNED_PDF), eq(documentId)))
                .thenReturn(storedDoc);
            doNothing().when(messagePublisher).publishReply(any(PdfStorageReplyEvent.class));

            service.handleProcessCommand(command);

            verify(storageService).storeDocument(eq(pdfContent), anyString(), eq(DocumentType.UNSIGNED_PDF), eq(documentId));
            ArgumentCaptor<PdfStorageReplyEvent> replyCaptor = ArgumentCaptor.forClass(PdfStorageReplyEvent.class);
            verify(messagePublisher).publishReply(replyCaptor.capture());
            assertEquals("saga-789", replyCaptor.getValue().getSagaId());
            assertEquals("stored-789", replyCaptor.getValue().getStoredDocumentId());
            assertNotNull(replyCaptor.getValue().getStoredDocumentUrl());
        }

        @Test
        @DisplayName("Should skip when unsigned PDF already exists")
        void shouldSkipWhenUnsignedPdfAlreadyExists() {
            String documentId = "doc-789";
            StoredDocument existingDoc = StoredDocument.builder()
                .id("existing-789")
                .invoiceId(documentId)
                .fileName("existing.pdf")
                .contentType("application/pdf")
                .storagePath("/2024/03/05/existing-789.pdf")
                .storageUrl("http://localhost:8084/api/v1/documents/existing-789")
                .fileSize(1024)
                .checksum("aaa111")
                .documentType(DocumentType.UNSIGNED_PDF)
                .build();

            ProcessPdfStorageCommand command = new ProcessPdfStorageCommand(
                "saga-789", SagaStep.PDF_STORAGE, "corr-789",
                documentId, "INV-2024-001", "INVOICE_PDF",
                "http://minio:9000/unsigned.pdf", 1024L
            );

            when(storageService.existsByInvoiceAndType(documentId, DocumentType.UNSIGNED_PDF)).thenReturn(true);
            when(storageService.getDocumentsByInvoice(documentId)).thenReturn(List.of(existingDoc));
            doNothing().when(messagePublisher).publishReply(any(PdfStorageReplyEvent.class));

            service.handleProcessCommand(command);

            verify(pdfDownloadPort, never()).downloadPdf(anyString());
            ArgumentCaptor<PdfStorageReplyEvent> replyCaptor = ArgumentCaptor.forClass(PdfStorageReplyEvent.class);
            verify(messagePublisher).publishReply(replyCaptor.capture());
            assertEquals("saga-789", replyCaptor.getValue().getSagaId());
            assertEquals("existing-789", replyCaptor.getValue().getStoredDocumentId());
        }
    }

    @Nested
    @DisplayName("handleCompensation(CompensateDocumentStorageCommand)")
    class CompensateDocumentStorageTests {

        @Test
        @DisplayName("Should delete all documents for invoice")
        void shouldDeleteAllDocumentsForInvoice() {
            String documentId = "doc-999";
            StoredDocument doc1 = StoredDocument.builder()
                .id("doc-1")
                .invoiceId(documentId)
                .fileName("file1.pdf")
                .contentType("application/pdf")
                .storagePath("/path/file1.pdf")
                .storageUrl("http://localhost:8084/api/v1/documents/doc-1")
                .fileSize(1024)
                .checksum("aaa111")
                .documentType(DocumentType.INVOICE_PDF)
                .build();
            StoredDocument doc2 = StoredDocument.builder()
                .id("doc-2")
                .invoiceId(documentId)
                .fileName("file2.xml")
                .contentType("application/xml")
                .storagePath("/path/file2.xml")
                .storageUrl("http://localhost:8084/api/v1/documents/doc-2")
                .fileSize(512)
                .checksum("bbb222")
                .documentType(DocumentType.SIGNED_XML)
                .build();

            CompensateDocumentStorageCommand command = new CompensateDocumentStorageCommand(
                "saga-999", SagaStep.STORE_DOCUMENT, "corr-999",
                SagaStep.STORE_DOCUMENT, documentId, "INVOICE_PDF"
            );

            when(storageService.getDocumentsByInvoice(documentId)).thenReturn(List.of(doc1, doc2));
            doNothing().when(storageService).deleteDocument(anyString());

            service.handleCompensation(command);

            verify(storageService).deleteDocument("doc-1");
            verify(storageService).deleteDocument("doc-2");
        }

        @Test
        @DisplayName("Should handle compensation when no documents exist")
        void shouldHandleCompensationWhenNoDocuments() {
            String documentId = "doc-999";
            CompensateDocumentStorageCommand command = new CompensateDocumentStorageCommand(
                "saga-999", SagaStep.STORE_DOCUMENT, "corr-999",
                SagaStep.STORE_DOCUMENT, documentId, "INVOICE_PDF"
            );

            when(storageService.getDocumentsByInvoice(documentId)).thenReturn(List.of());

            service.handleCompensation(command);

            verify(storageService, never()).deleteDocument(anyString());
        }

        @Test
        @DisplayName("Should continue compensation when one deletion fails")
        void shouldContinueWhenOneDeletionFails() {
            String documentId = "doc-999";
            StoredDocument doc1 = StoredDocument.builder()
                .id("doc-1")
                .invoiceId(documentId)
                .fileName("file1.pdf")
                .contentType("application/pdf")
                .storagePath("/path/file1.pdf")
                .storageUrl("http://localhost:8084/api/v1/documents/doc-1")
                .fileSize(1024)
                .checksum("aaa111")
                .documentType(DocumentType.INVOICE_PDF)
                .build();
            StoredDocument doc2 = StoredDocument.builder()
                .id("doc-2")
                .invoiceId(documentId)
                .fileName("file2.xml")
                .contentType("application/xml")
                .storagePath("/path/file2.xml")
                .storageUrl("http://localhost:8084/api/v1/documents/doc-2")
                .fileSize(512)
                .checksum("bbb222")
                .documentType(DocumentType.SIGNED_XML)
                .build();

            CompensateDocumentStorageCommand command = new CompensateDocumentStorageCommand(
                "saga-999", SagaStep.STORE_DOCUMENT, "corr-999",
                SagaStep.STORE_DOCUMENT, documentId, "INVOICE_PDF"
            );

            when(storageService.getDocumentsByInvoice(documentId)).thenReturn(List.of(doc1, doc2));
            doThrow(new RuntimeException("Delete failed")).when(storageService).deleteDocument("doc-1");
            doNothing().when(storageService).deleteDocument("doc-2");

            service.handleCompensation(command);

            verify(storageService).deleteDocument("doc-1");
            verify(storageService).deleteDocument("doc-2");
        }
    }

    @Nested
    @DisplayName("handleCompensation(CompensateSignedXmlStorageCommand)")
    class CompensateSignedXmlStorageTests {

        @Test
        @DisplayName("Should delete signed XML document")
        void shouldDeleteSignedXmlDocument() {
            String documentId = "doc-888";
            StoredDocument xmlDoc = StoredDocument.builder()
                .id("xml-doc-1")
                .invoiceId(documentId)
                .fileName("signed.xml")
                .contentType("application/xml")
                .storagePath("/path/signed.xml")
                .storageUrl("http://localhost:8084/api/v1/documents/xml-doc-1")
                .fileSize(256)
                .checksum("ccc333")
                .documentType(DocumentType.SIGNED_XML)
                .build();

            CompensateSignedXmlStorageCommand command = new CompensateSignedXmlStorageCommand(
                "saga-888", SagaStep.SIGNEDXML_STORAGE, "corr-888",
                SagaStep.SIGNEDXML_STORAGE, documentId, "INVOICE"
            );

            when(storageService.getDocumentsByInvoice(documentId)).thenReturn(List.of(xmlDoc));
            doNothing().when(storageService).deleteDocument("xml-doc-1");

            service.handleCompensation(command);

            verify(storageService).deleteDocument("xml-doc-1");
        }

        @Test
        @DisplayName("Should not delete non-SIGNED_XML documents")
        void shouldNotDeleteNonSignedXmlDocuments() {
            String documentId = "doc-888";
            StoredDocument pdfDoc = StoredDocument.builder()
                .id("pdf-doc-1")
                .invoiceId(documentId)
                .fileName("file.pdf")
                .contentType("application/pdf")
                .storagePath("/path/file.pdf")
                .storageUrl("http://localhost:8084/api/v1/documents/pdf-doc-1")
                .fileSize(1024)
                .checksum("ddd444")
                .documentType(DocumentType.INVOICE_PDF)
                .build();

            CompensateSignedXmlStorageCommand command = new CompensateSignedXmlStorageCommand(
                "saga-888", SagaStep.SIGNEDXML_STORAGE, "corr-888",
                SagaStep.SIGNEDXML_STORAGE, documentId, "INVOICE"
            );

            when(storageService.getDocumentsByInvoice(documentId)).thenReturn(List.of(pdfDoc));

            service.handleCompensation(command);

            verify(storageService, never()).deleteDocument(anyString());
        }
    }

    @Nested
    @DisplayName("handleCompensation(CompensatePdfStorageCommand)")
    class CompensatePdfStorageTests {

        @Test
        @DisplayName("Should delete UNSIGNED_PDF document")
        void shouldDeleteUnsignedPdfDocument() {
            String documentId = "doc-777";
            StoredDocument unsignedPdf = StoredDocument.builder()
                .id("pdf-doc-1")
                .invoiceId(documentId)
                .fileName("unsigned.pdf")
                .contentType("application/pdf")
                .storagePath("/path/unsigned.pdf")
                .storageUrl("http://localhost:8084/api/v1/documents/pdf-doc-1")
                .fileSize(512)
                .checksum("eee555")
                .documentType(DocumentType.UNSIGNED_PDF)
                .build();

            CompensatePdfStorageCommand command = new CompensatePdfStorageCommand(
                "saga-777", SagaStep.PDF_STORAGE, "corr-777",
                SagaStep.PDF_STORAGE, documentId, "INVOICE_PDF"
            );

            when(storageService.getDocumentsByInvoice(documentId)).thenReturn(List.of(unsignedPdf));
            doNothing().when(storageService).deleteDocument("pdf-doc-1");

            service.handleCompensation(command);

            verify(storageService).deleteDocument("pdf-doc-1");
        }

        @Test
        @DisplayName("Should not delete non-UNSIGNED_PDF documents")
        void shouldNotDeleteNonUnsignedPdfDocuments() {
            String documentId = "doc-777";
            StoredDocument signedPdf = StoredDocument.builder()
                .id("pdf-doc-1")
                .invoiceId(documentId)
                .fileName("signed.pdf")
                .contentType("application/pdf")
                .storagePath("/path/signed.pdf")
                .storageUrl("http://localhost:8084/api/v1/documents/pdf-doc-1")
                .fileSize(1024)
                .checksum("fff666")
                .documentType(DocumentType.INVOICE_PDF)
                .build();

            CompensatePdfStorageCommand command = new CompensatePdfStorageCommand(
                "saga-777", SagaStep.PDF_STORAGE, "corr-777",
                SagaStep.PDF_STORAGE, documentId, "INVOICE_PDF"
            );

            when(storageService.getDocumentsByInvoice(documentId)).thenReturn(List.of(signedPdf));

            service.handleCompensation(command);

            verify(storageService, never()).deleteDocument(anyString());
        }
    }
}
