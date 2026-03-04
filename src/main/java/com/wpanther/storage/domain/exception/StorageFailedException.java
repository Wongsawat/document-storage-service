package com.wpanther.storage.domain.exception;

public class StorageFailedException extends DomainException {

    public StorageFailedException(String reason) {
        super("Storage operation failed: " + reason);
    }

    public StorageFailedException(String reason, Throwable cause) {
        super("Storage operation failed: " + reason, cause);
    }
}
