package com.wpanther.storage.domain.exception;

public class InvalidDocumentException extends DomainException {

    public InvalidDocumentException(String message) {
        super("Invalid document: " + message);
    }

    public InvalidDocumentException(String message, Throwable cause) {
        super("Invalid document: " + message, cause);
    }
}
