package com.wpanther.storage.application.usecase;

import com.wpanther.storage.application.dto.event.*;
import com.wpanther.storage.domain.exception.StorageFailedException;
import com.wpanther.storage.domain.exception.InvalidDocumentException;
import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.application.port.out.MessagePublisherPort;
import com.wpanther.storage.application.port.out.PdfDownloadPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Domain service for saga command orchestration.
 * Implements SagaCommandUseCase port.
 * Handles document storage commands from the orchestrator.
 */
@Service
public class SagaOrchestrationService implements SagaCommandUseCase {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrationService.class);

    private final FileStorageDomainService storageService;
    private final PdfDownloadPort pdfDownloadPort;
    private final MessagePublisherPort messagePublisher;

    public SagaOrchestrationService(FileStorageDomainService storageService,
                                     PdfDownloadPort pdfDownloadPort,
                                     MessagePublisherPort messagePublisher) {
        this.storageService = storageService;
        this.pdfDownloadPort = pdfDownloadPort;
        this.messagePublisher = messagePublisher;
    }

    @Override
    @Transactional
    public void handleProcessCommand(ProcessDocumentStorageCommand command) {
        log.info("Handling ProcessDocumentStorageCommand for saga: {}, document: {}",
                 command.getSagaId(), command.getDocumentId());

        try {
            // 1. Idempotency check
            if (storageService.existsByInvoiceAndType(command.getDocumentId(), DocumentType.INVOICE_PDF)) {
                log.info("Document already exists for invoice: {}, type: INVOICE_PDF",
                         command.getDocumentId());
                publishAlreadyExistsReply(command);
                return;
            }

            // 2. Download PDF from orchestrator-provided URL
            byte[] content = pdfDownloadPort.downloadPdf(command.getSignedPdfUrl());

            // 3. Store document
            String filename = command.getDocumentId() + ".pdf";
            StoredDocument stored = storageService.storeDocument(
                content,
                filename,
                DocumentType.INVOICE_PDF,
                command.getDocumentId()
            );

            // 4. Publish event and reply via outbox
            DocumentStoredEvent event = new DocumentStoredEvent(
                stored.getId(),
                stored.getInvoiceId(),
                command.getInvoiceNumber(),
                stored.getFileName(),
                stored.getStorageUrl(),
                stored.getFileSize(),
                stored.getChecksum(),
                stored.getDocumentType().name(),
                command.getCorrelationId()
            );
            messagePublisher.publishEvent(event);

            DocumentStorageReplyEvent reply = DocumentStorageReplyEvent.success(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId()
            );
            messagePublisher.publishReply(reply);

            log.info("Successfully processed ProcessDocumentStorageCommand for saga: {}",
                     command.getSagaId());

        } catch (StorageFailedException | InvalidDocumentException e) {
            log.error("Failed to process document storage command for saga: {}",
                      command.getSagaId(), e);
            publishFailureReply(command, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing document storage command for saga: {}",
                      command.getSagaId(), e);
            publishFailureReply(command, "Unexpected error: " + e.getMessage());
        }
    }

    private void publishAlreadyExistsReply(ProcessDocumentStorageCommand command) {
        DocumentStorageReplyEvent reply = DocumentStorageReplyEvent.success(
            command.getSagaId(),
            command.getSagaStep(),
            command.getCorrelationId()
        );
        messagePublisher.publishReply(reply);
    }

    private void publishFailureReply(ProcessDocumentStorageCommand command, String error) {
        DocumentStorageReplyEvent reply = DocumentStorageReplyEvent.failure(
            command.getSagaId(),
            command.getSagaStep(),
            command.getCorrelationId(),
            error
        );
        messagePublisher.publishReply(reply);
    }

    @Override
    @Transactional
    public void handleProcessCommand(ProcessSignedXmlStorageCommand command) {
        log.info("Handling ProcessSignedXmlStorageCommand for saga: {}, document: {}",
                 command.getSagaId(), command.getDocumentId());

        try {
            // 1. Idempotency check
            if (storageService.existsByInvoiceAndType(command.getDocumentId(), DocumentType.SIGNED_XML)) {
                log.info("Signed XML already exists for invoice: {}", command.getDocumentId());
                publishAlreadyExistsXmlReply(command);
                return;
            }

            // 2. Download signed XML from URL
            String xmlContent = pdfDownloadPort.downloadContent(command.getSignedXmlUrl());
            if (xmlContent == null || xmlContent.isBlank()) {
                throw new StorageFailedException("Failed to download signed XML from " + command.getSignedXmlUrl());
            }

            // 3. Store signed XML content
            byte[] content = xmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String filename = generateXmlFileName(command.getInvoiceNumber(), command.getDocumentType());

            StoredDocument stored = storageService.storeDocument(
                content,
                filename,
                DocumentType.SIGNED_XML,
                command.getDocumentId()
            );

            // 4. Publish reply via outbox
            SignedXmlStorageReplyEvent reply = SignedXmlStorageReplyEvent.success(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId()
            );
            messagePublisher.publishReply(reply);

            log.info("Successfully stored signed XML for saga: {}", command.getSagaId());

        } catch (StorageFailedException | InvalidDocumentException e) {
            log.error("Failed to store signed XML for saga: {}", command.getSagaId(), e);
            publishFailureXmlReply(command, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error storing signed XML for saga: {}", command.getSagaId(), e);
            publishFailureXmlReply(command, "Unexpected error: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void handleProcessCommand(ProcessPdfStorageCommand command) {
        log.info("Handling ProcessPdfStorageCommand for saga: {}, pdfUrl: {}",
                 command.getSagaId(), command.getPdfUrl());

        try {
            // 1. Idempotency check - UNSIGNED_PDF type for this step
            if (storageService.existsByInvoiceAndType(command.getDocumentId(), DocumentType.UNSIGNED_PDF)) {
                log.info("Unsigned PDF already exists for invoice: {}", command.getDocumentId());
                publishAlreadyExistsPdfReply(command);
                return;
            }

            // 2. Download unsigned PDF from MinIO
            byte[] content = pdfDownloadPort.downloadPdf(command.getPdfUrl());

            // 3. Verify size matches expected
            if (content.length != command.getPdfSize()) {
                throw new StorageFailedException(
                    "Downloaded PDF size " + content.length + " does not match expected " + command.getPdfSize());
            }

            // 4. Store as UNSIGNED_PDF
            String filename = command.getDocumentId() + "_unsigned.pdf";

            StoredDocument stored = storageService.storeDocument(
                content,
                filename,
                DocumentType.UNSIGNED_PDF,
                command.getDocumentId()
            );

            // 5. Publish reply with storedDocumentUrl for SIGN_PDF step
            String storedUrl = "/api/v1/documents/" + stored.getId() + "/download";
            PdfStorageReplyEvent reply = PdfStorageReplyEvent.success(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId(),
                stored.getId(),
                storedUrl
            );
            messagePublisher.publishReply(reply);

            log.info("Successfully stored unsigned PDF for saga: {}, storedUrl: {}",
                     command.getSagaId(), storedUrl);

        } catch (StorageFailedException | InvalidDocumentException e) {
            log.error("Failed to store unsigned PDF for saga: {}", command.getSagaId(), e);
            publishFailurePdfReply(command, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error storing unsigned PDF for saga: {}", command.getSagaId(), e);
            publishFailurePdfReply(command, "Unexpected error: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void handleCompensation(CompensateDocumentStorageCommand command) {
        log.info("Handling CompensateDocumentStorageCommand for saga: {}, document: {}",
                 command.getSagaId(), command.getDocumentId());

        // Idempotent deletion - delete all documents for the invoice
        List<StoredDocument> documents = storageService.getDocumentsByInvoice(command.getDocumentId());

        for (StoredDocument doc : documents) {
            try {
                storageService.deleteDocument(doc.getId());
                log.info("Compensated document storage: {} for saga: {}", doc.getId(), command.getSagaId());
            } catch (Exception e) {
                log.warn("Failed to delete document: {} during compensation for saga: {}",
                         doc.getId(), command.getSagaId(), e);
            }
        }

        log.info("Completed compensation for saga: {}", command.getSagaId());
    }

    @Override
    @Transactional
    public void handleCompensation(CompensateSignedXmlStorageCommand command) {
        log.info("Handling CompensateSignedXmlStorageCommand for saga: {}, document: {}",
                 command.getSagaId(), command.getDocumentId());

        // Delete signed XML document
        storageService.getDocumentsByInvoice(command.getDocumentId())
            .stream()
            .filter(doc -> doc.getDocumentType() == DocumentType.SIGNED_XML)
            .findFirst()
            .ifPresent(doc -> {
                storageService.deleteDocument(doc.getId());
                log.info("Compensated signed XML storage: {} for saga: {}", doc.getId(), command.getSagaId());
            });

        log.info("Completed signed XML compensation for saga: {}", command.getSagaId());
    }

    @Override
    @Transactional
    public void handleCompensation(CompensatePdfStorageCommand command) {
        log.info("Handling CompensatePdfStorageCommand for saga: {}, document: {}",
                 command.getSagaId(), command.getDocumentId());

        // Delete UNSIGNED_PDF document
        storageService.getDocumentsByInvoice(command.getDocumentId())
            .stream()
            .filter(doc -> doc.getDocumentType() == DocumentType.UNSIGNED_PDF)
            .findFirst()
            .ifPresent(doc -> {
                storageService.deleteDocument(doc.getId());
                log.info("Compensated unsigned PDF storage: {} for saga: {}", doc.getId(), command.getSagaId());
            });

        log.info("Completed PDF storage compensation for saga: {}", command.getSagaId());
    }

    private void publishAlreadyExistsXmlReply(ProcessSignedXmlStorageCommand command) {
        SignedXmlStorageReplyEvent reply = SignedXmlStorageReplyEvent.success(
            command.getSagaId(),
            command.getSagaStep(),
            command.getCorrelationId()
        );
        messagePublisher.publishReply(reply);
    }

    private void publishFailureXmlReply(ProcessSignedXmlStorageCommand command, String error) {
        SignedXmlStorageReplyEvent reply = SignedXmlStorageReplyEvent.failure(
            command.getSagaId(),
            command.getSagaStep(),
            command.getCorrelationId(),
            error
        );
        messagePublisher.publishReply(reply);
    }

    private void publishAlreadyExistsPdfReply(ProcessPdfStorageCommand command) {
        Optional<StoredDocument> doc = storageService.getDocumentsByInvoice(command.getDocumentId())
            .stream()
            .filter(d -> d.getDocumentType() == DocumentType.UNSIGNED_PDF)
            .findFirst();

        if (doc.isPresent()) {
            StoredDocument stored = doc.get();
            String storedUrl = "/api/v1/documents/" + stored.getId() + "/download";
            PdfStorageReplyEvent reply = PdfStorageReplyEvent.success(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId(),
                stored.getId(),
                storedUrl
            );
            messagePublisher.publishReply(reply);
        } else {
            // Document disappeared between idempotency check and reply — log and fail
            log.warn("Unsigned PDF document disappeared during idempotency check for documentId: {}",
                     command.getDocumentId());
            publishFailurePdfReply(command, "Document disappeared during idempotency check");
        }
    }

    private void publishFailurePdfReply(ProcessPdfStorageCommand command, String error) {
        PdfStorageReplyEvent reply = PdfStorageReplyEvent.failure(
            command.getSagaId(),
            command.getSagaStep(),
            command.getCorrelationId(),
            error
        );
        messagePublisher.publishReply(reply);
    }

    private String generateXmlFileName(String invoiceNumber, String documentType) {
        String typePrefix = getTypePrefix(documentType);
        return String.format("%s_%s_signed.xml", typePrefix, invoiceNumber);
    }

    private String getTypePrefix(String documentType) {
        return switch (documentType.toUpperCase()) {
            case "INVOICE" -> "INV";
            case "TAX_INVOICE" -> "TAX";
            case "RECEIPT" -> "RCT";
            case "DEBIT_CREDIT_NOTE" -> "DCN";
            case "CANCELLATION_NOTE" -> "CN";
            case "ABBREVIATED_TAX_INVOICE" -> "ABT";
            default -> "DOC";
        };
    }
}
