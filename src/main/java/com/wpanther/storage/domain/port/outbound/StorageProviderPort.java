package com.wpanther.storage.domain.port.outbound;

import com.wpanther.storage.domain.model.StorageResult;
import com.wpanther.storage.domain.model.StorageException;
import java.io.InputStream;

/**
 * Outbound port for file storage operations.
 * Implemented by LocalFileStorageAdapter and S3FileStorageAdapter.
 */
public interface StorageProviderPort {
    StorageResult store(String documentId, InputStream content,
                        String originalFilename, long size) throws StorageException;
    InputStream retrieve(String storageLocation) throws StorageException;
    void delete(String storageLocation) throws StorageException;
    boolean exists(String storageLocation);
}
