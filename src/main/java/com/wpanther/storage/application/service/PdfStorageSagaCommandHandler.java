package com.wpanther.storage.application.service;

import com.wpanther.storage.domain.event.CompensatePdfStorageCommand;
import com.wpanther.storage.domain.event.ProcessPdfStorageCommand;
import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.infrastructure.messaging.PdfStorageSagaReplyPublisher;
import com.wpanther.storage.infrastructure.persistence.StoredDocumentEntity;
import com.wpanther.storage.infrastructure.persistence.MongoDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles saga commands from the orchestrator for the PDF_STORAGE step.
 * Downloads the unsigned tax invoice PDF from MinIO and stores it in document-storage-service.
 * Publishes replies via outbox pattern.
 * <p>
 * Uses UNSIGNED_PDF document type to prevent idempotency collision with the
 * STORE_DOCUMENT step (which stores the signed PDF as INVOICE_PDF for the same documentId).
 * <p>
 * <b>Transaction Boundary:</b>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ @Transactional (PostgreSQL transaction only)                    │
 * │ ┌─────────────────────────────────────────────────────────────┐│
 * │ │ 1. Idempotency check (MongoDB - NOT in PostgreSQL tx)     ││
 * │ │ 2. Download unsigned PDF from MinIO (HTTP call)          ││
 * │ │ 3. Store document as UNSIGNED_PDF (MongoDB + filesystem) ││
 * │ │ 4. Publish saga reply (PostgreSQL outbox - IN tx)        ││
 * │ └─────────────────────────────────────────────────────────────┘│
 * │                    COMMIT (PostgreSQL only)                   │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 * <p>
 * <b>Important Notes:</b>
 * <ul>
 *   <li>MongoDB writes are NOT part of the PostgreSQL transaction</li>
 *   <li>Idempotency check prevents duplicate processing on retry</li>
 *   <li>Compensation handler cleans up orphaned MongoDB documents</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfStorageSagaCommandHandler {

    private final DocumentStorageService storageService;
    private final PdfDownloadService downloadService;
    private final MongoDocumentRepository documentRepository;
    private final PdfStorageSagaReplyPublisher sagaReplyPublisher;

    @Transactional
    public void handleProcessCommand(ProcessPdfStorageCommand command) {
        log.info("Handling ProcessPdfStorageCommand for saga {} document {}",
                command.getSagaId(), command.getDocumentId());

        try {
            // Idempotency: check if unsigned PDF already stored for this documentId
            List<StoredDocumentEntity> existing = documentRepository
                    .findByInvoiceIdAndDocumentType(command.getDocumentId(), DocumentType.UNSIGNED_PDF);

            if (!existing.isEmpty()) {
                StoredDocumentEntity existingDoc = existing.get(0);
                log.warn("Unsigned PDF for documentId {} already stored as {}, sending SUCCESS reply",
                        command.getDocumentId(), existingDoc.getId());
                sagaReplyPublisher.publishSuccess(
                        command.getSagaId(), command.getSagaStep(), command.getCorrelationId(),
                        existingDoc.getId(), existingDoc.getStorageUrl());
                return;
            }

            // Download PDF from MinIO URL
            byte[] pdfContent = downloadService.downloadPdf(command.getPdfUrl());
            String fileName = downloadService.extractFileName(command.getPdfUrl(), command.getInvoiceNumber());

            // Store unsigned PDF in document-storage-service
            StoredDocument document = storageService.storeDocument(
                    pdfContent,
                    fileName,
                    "application/pdf",
                    DocumentType.UNSIGNED_PDF,
                    command.getDocumentId(),
                    command.getInvoiceNumber()
            );

            log.info("Stored unsigned PDF: documentId={}, storedId={}, url={}",
                    command.getDocumentId(), document.getId(), document.getStorageUrl());

            // Publish SUCCESS reply with storedDocumentId and storedDocumentUrl
            // The orchestrator stores these in metadata so SIGN_PDF can download the PDF
            sagaReplyPublisher.publishSuccess(
                    command.getSagaId(), command.getSagaStep(), command.getCorrelationId(),
                    document.getId(), document.getStorageUrl());

            log.info("Successfully processed PDF storage for saga {} document {}",
                    command.getSagaId(), command.getDocumentId());

        } catch (Exception e) {
            log.error("Failed to process PDF storage for saga {} document {}: {}",
                    command.getSagaId(), command.getDocumentId(), e.getMessage(), e);
            sagaReplyPublisher.publishFailure(
                    command.getSagaId(), command.getSagaStep(), command.getCorrelationId(), e.getMessage());
        }
    }

    @Transactional
    public void handleCompensation(CompensatePdfStorageCommand command) {
        log.info("Handling compensation for PDF storage for saga {} document {}",
                command.getSagaId(), command.getDocumentId());

        try {
            List<StoredDocumentEntity> documents = documentRepository
                    .findByInvoiceIdAndDocumentType(command.getDocumentId(), DocumentType.UNSIGNED_PDF);

            if (!documents.isEmpty()) {
                for (StoredDocumentEntity doc : documents) {
                    storageService.deleteDocument(doc.getId());
                }
                log.info("Deleted {} unsigned PDF document(s) for compensation of documentId {}",
                        documents.size(), command.getDocumentId());
            } else {
                log.info("No unsigned PDF documents found for documentId {} - already compensated or never stored",
                        command.getDocumentId());
            }

            sagaReplyPublisher.publishCompensated(
                    command.getSagaId(), command.getSagaStep(), command.getCorrelationId());

        } catch (Exception e) {
            log.error("Failed to compensate PDF storage for saga {} document {}: {}",
                    command.getSagaId(), command.getDocumentId(), e.getMessage(), e);
            sagaReplyPublisher.publishFailure(
                    command.getSagaId(), command.getSagaStep(), command.getCorrelationId(),
                    "Compensation failed: " + e.getMessage());
        }
    }
}
