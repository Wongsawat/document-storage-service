package com.invoice.storage.domain.service;

/**
 * Interface for file storage providers
 */
public interface FileStorageProvider {

    /**
     * Store file content
     *
     * @param content File content
     * @param fileName File name
     * @return Storage result with path and URL
     * @throws StorageException if storage fails
     */
    StorageResult store(byte[] content, String fileName) throws StorageException;

    /**
     * Retrieve file content
     *
     * @param path Storage path
     * @return File content
     * @throws StorageException if retrieval fails
     */
    byte[] retrieve(String path) throws StorageException;

    /**
     * Delete file
     *
     * @param path Storage path
     * @throws StorageException if deletion fails
     */
    void delete(String path) throws StorageException;

    /**
     * Check if file exists
     *
     * @param path Storage path
     * @return true if file exists
     */
    boolean exists(String path);

    /**
     * Storage result containing path and URL
     */
    record StorageResult(String path, String url) {}

    /**
     * Exception thrown when storage operations fail
     */
    class StorageException extends Exception {
        public StorageException(String message) {
            super(message);
        }

        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
