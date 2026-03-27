package com.wpanther.storage.application.port.out;

import com.wpanther.storage.domain.model.DocumentType;

/**
 * Outbound port for recording business metrics.
 * <p>
 * Implementations bridge to infrastructure-specific metrics systems
 * (e.g., Micrometer, custom registries) without leaking framework details
 * into the application layer.
 * </p>
 */
public interface MetricsPort {

    /**
     * Record that a document was stored.
     *
     * @param documentType the type of document stored
     */
    void recordDocumentStored(DocumentType documentType);

    /**
     * Record that a document was retrieved.
     */
    void recordDocumentRetrieved();

    /**
     * Record that a document was deleted.
     */
    void recordDocumentDeleted();

    /**
     * Start timing a storage operation. Call {@code run()} when done.
     * <p>
     * Typical usage:
     * <pre>
     * Runnable timer = metrics.timeStorageOperation();
     * try {
     *     // storage operation here
     * } finally {
     *     timer.run();
     * }
     * </pre>
     *
     * @return a Runnable that records elapsed time when invoked
     */
    Runnable timeStorageOperation();
}
