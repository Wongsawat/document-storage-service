package com.wpanther.storage.application.port.out;

import com.wpanther.storage.domain.model.StorageResult;
import com.wpanther.storage.domain.model.StorageException;
import java.io.InputStream;

/**
 * Outbound port for file storage operations.
 * <p>
 * This port abstracts the physical storage backend, allowing the domain to interact
 * with storage without coupling to specific implementations. Implementations include:
 * </p>
 * <ul>
 *   <li>{@link com.wpanther.storage.infrastructure.adapter.outbound.storage.LocalFileStorageAdapter}</li>
 *   <li>{@link com.wpanther.storage.infrastructure.adapter.outbound.storage.S3FileStorageAdapter}</li>
 * </ul>
 * <p>
 * Implementations must be thread-safe and handle concurrent access appropriately.
 * The storageLocation returned by {@link #store} is opaque to the domain and should
 * be treated as an identifier by clients.
 * </p>
 */
public interface StorageProviderPort {

    /**
     * Store a document in the backing storage system.
     * <p>
     * The implementer is responsible for:
     * </p>
     * <ul>
     *   <li>Choosing the final storage location (e.g., date-based directory structure)</li>
     *   <li>Creating any necessary directories/containers</li>
     *   <li>Closing the InputStream after consumption</li>
     *   <li>Returning an opaque location identifier for later retrieval</li>
     * </ul>
     *
     * @param documentId the unique document identifier (for naming/logging purposes)
     * @param content the document content as an InputStream (will be consumed)
     * @param originalFilename the original filename for extension/type detection
     * @param size the content size in bytes (for validation/logging)
     * @return StorageResult containing the opaque storage location and provider name
     * @throws StorageException if the storage operation fails
     */
    StorageResult store(String documentId, InputStream content,
                        String originalFilename, long size) throws StorageException;

    /**
     * Retrieve a document from storage.
     * <p>
     * Returns an InputStream for reading the document content. The caller is
     * responsible for closing the stream.
     * </p>
     *
     * @param storageLocation the opaque location identifier returned by {@link #store}
     * @return InputStream containing the document content
     * @throws StorageException if the file cannot be found or read
     */
    InputStream retrieve(String storageLocation) throws StorageException;

    /**
     * Delete a document from storage.
     * <p>
     * Implementations should be idempotent - if the file doesn't exist,
     * the operation should succeed without throwing an exception.
     * </p>
     *
     * @param storageLocation the opaque location identifier of the file to delete
     * @throws StorageException if the deletion fails (excluding file-not-found cases)
     */
    void delete(String storageLocation) throws StorageException;

    /**
     * Check if a document exists at the given storage location.
     * <p>
     * This method should not throw exceptions for missing files - return false
     * instead. Only throw for actual errors (e.g., connectivity issues).
     * </p>
     *
     * @param storageLocation the opaque location identifier to check
     * @return true if the file exists, false otherwise
     */
    boolean exists(String storageLocation);
}
