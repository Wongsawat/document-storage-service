package com.invoice.storage.domain.model;

/**
 * Enum representing the type of stored document
 */
public enum DocumentType {
    /**
     * Invoice PDF document
     */
    INVOICE_PDF,

    /**
     * Invoice XML document
     */
    INVOICE_XML,

    /**
     * Generic attachment
     */
    ATTACHMENT,

    /**
     * Other document type
     */
    OTHER
}
