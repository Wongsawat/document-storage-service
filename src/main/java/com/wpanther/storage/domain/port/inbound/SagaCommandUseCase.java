package com.wpanther.storage.domain.port.inbound;

import com.wpanther.storage.domain.event.*;

/**
 * Inbound port for saga command handling.
 * <p>
 * This port defines the saga orchestration interface for document storage operations,
 * implemented by {@link com.wpanther.storage.domain.service.SagaOrchestrationService}
 * and called by {@link com.wpanther.storage.infrastructure.adapter.inbound.messaging.SagaCommandAdapter}.
 * </p>
 * <p>
 * The port supports three distinct saga steps in the invoice processing pipeline:
 * </p>
 * <ul>
 *   <li><b>PDF_STORAGE</b> (step 6): Store unsigned PDF from MinIO as {@code UNSIGNED_PDF}</li>
 *   <li><b>SIGNEDXML_STORAGE</b> (step 4): Store signed XML as {@code SIGNED_XML}</li>
 *   <li><b>STORE_DOCUMENT</b> (step 8): Store signed PDF as {@code INVOICE_PDF}</li>
 * </ul>
 * <p>
 * Each step has a process command (forward operation) and a compensation command
 * (rollback). All commands are consumed from Kafka and published to the outbox
 * for exactly-once delivery.
 * </p>
 * <p>
 * <b>Idempotency:</b> All process handlers check for existing documents before
 * storage, allowing safe retry without duplication.
 * </p>
 * <p>
 * <b>Error Handling:</b> Errors are caught and published as failure replies
 * rather than thrown, preventing saga coordinator crashes.
 * </p>
 */
public interface SagaCommandUseCase {

    /**
     * Handle the STORE_DOCUMENT saga step (step 8).
     * <p>
     * Downloads the signed PDF from the orchestrator-provided URL and stores it
     * as {@code INVOICE_PDF}. Publishes both a {@code DocumentStoredEvent} (for
     * downstream notification) and a saga reply (for orchestrator coordination).
     * </p>
     * <p>
     * <b>Idempotency:</b> Returns success if an {@code INVOICE_PDF} already exists
     * for the given invoice ID.
     * </p>
     *
     * @param command the process command containing saga ID, document ID, and signed PDF URL
     */
    void handleProcessCommand(ProcessDocumentStorageCommand command);

    /**
     * Handle the SIGNEDXML_STORAGE saga step (step 4).
     * <p>
     * Downloads the signed XML from the orchestrator-provided URL and stores it
     * as {@code SIGNED_XML}. Publishes a saga reply for orchestrator coordination.
     * </p>
     * <p>
     * <b>Idempotency:</b> Returns success if a {@code SIGNED_XML} already exists
     * for the given invoice ID.
     * </p>
     *
     * @param command the process command containing saga ID, document ID, and signed XML URL
     */
    void handleProcessCommand(ProcessSignedXmlStorageCommand command);

    /**
     * Handle the PDF_STORAGE saga step (step 6).
     * <p>
     * Downloads the unsigned PDF from MinIO and stores it as {@code UNSIGNED_PDF}.
     * The reply includes {@code storedDocumentId} and {@code storedDocumentUrl} for
     * the SIGN_PDF step to download and sign.
     * </p>
     * <p>
     * <b>Idempotency:</b> Returns success with existing document URL if an
     * {@code UNSIGNED_PDF} already exists for the given invoice ID.
     * </p>
     *
     * @param command the process command containing saga ID, document ID, PDF URL, and size
     */
    void handleProcessCommand(ProcessPdfStorageCommand command);

    /**
     * Handle compensation for the STORE_DOCUMENT step.
     * <p>
     * Deletes ALL documents associated with the invoice (PDF, XML, etc.).
     * This ensures complete rollback on saga failure.
     * </p>
     * <p>
     * <b>Idempotency:</b> Ignores missing documents (no-op if already deleted).
     * </p>
     *
     * @param command the compensation command containing saga ID and document ID
     */
    void handleCompensation(CompensateDocumentStorageCommand command);

    /**
     * Handle compensation for the SIGNEDXML_STORAGE step.
     * <p>
     * Deletes only the {@code SIGNED_XML} document for the invoice.
     * </p>
     * <p>
     * <b>Idempotency:</b> Ignores missing documents.
     * </p>
     *
     * @param command the compensation command containing saga ID and document ID
     */
    void handleCompensation(CompensateSignedXmlStorageCommand command);

    /**
     * Handle compensation for the PDF_STORAGE step.
     * <p>
     * Deletes only the {@code UNSIGNED_PDF} document for the invoice.
     * </p>
     * <p>
     * <b>Idempotency:</b> Ignores missing documents.
     * </p>
     *
     * @param command the compensation command containing saga ID and document ID
     */
    void handleCompensation(CompensatePdfStorageCommand command);
}
