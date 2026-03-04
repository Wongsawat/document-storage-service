package com.wpanther.storage.domain.exception;

public class DocumentNotFoundException extends DomainException {

    private final String documentId;

    public DocumentNotFoundException(String documentId) {
        super("Document not found: " + documentId);
        this.documentId = documentId;
    }

    public String documentId() {
        return documentId;
    }
}
