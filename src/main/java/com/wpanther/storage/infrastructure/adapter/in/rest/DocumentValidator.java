package com.wpanther.storage.infrastructure.adapter.in.rest;

import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.regex.Pattern;

/**
 * Utility class for validating document-related inputs.
 */
public final class DocumentValidator {

    // Invoice ID pattern: alphanumeric, hyphens, underscores, 1-100 chars
    private static final Pattern INVOICE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,100}$");

    // Invoice number pattern: more flexible, allows common invoice number formats
    private static final Pattern INVOICE_NUMBER_PATTERN = Pattern.compile("^[a-zA-Z0-9/\\-_.]{1,50}$");

    // Document ID pattern (UUID): standard UUID format
    private static final Pattern DOCUMENT_ID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    // Max file size: 100MB
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;

    private DocumentValidator() {
        // Utility class - prevent instantiation
    }

    /**
     * Validate an invoice ID.
     *
     * @param invoiceId the invoice ID to validate
     * @throws IllegalArgumentException if invalid
     */
    public static void validateInvoiceId(String invoiceId) {
        if (!StringUtils.hasText(invoiceId)) {
            throw new IllegalArgumentException("Invoice ID cannot be empty");
        }
        if (!INVOICE_ID_PATTERN.matcher(invoiceId).matches()) {
            throw new IllegalArgumentException(
                "Invoice ID must be 1-100 alphanumeric characters (hyphens and underscores allowed)"
            );
        }
    }

    /**
     * Validate an invoice number.
     *
     * @param invoiceNumber the invoice number to validate (can be null/empty)
     * @throws IllegalArgumentException if invalid format
     */
    public static void validateInvoiceNumber(String invoiceNumber) {
        if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
            return; // Optional field
        }
        if (!INVOICE_NUMBER_PATTERN.matcher(invoiceNumber).matches()) {
            throw new IllegalArgumentException(
                "Invoice number must be 1-50 characters (alphanumeric, /, -, _, . allowed)"
            );
        }
    }

    /**
     * Validate a document ID (UUID format).
     *
     * @param documentId the document ID to validate
     * @throws IllegalArgumentException if invalid
     */
    public static void validateDocumentId(String documentId) {
        if (!StringUtils.hasText(documentId)) {
            throw new IllegalArgumentException("Document ID cannot be empty");
        }
        if (!DOCUMENT_ID_PATTERN.matcher(documentId).matches()) {
            throw new IllegalArgumentException("Document ID must be a valid UUID format");
        }
    }

    /**
     * Validate a multipart file.
     *
     * @param file the file to validate
     * @throws IllegalArgumentException if invalid
     */
    public static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                "File size exceeds maximum allowed size of " + (MAX_FILE_SIZE / 1024 / 1024) + "MB"
            );
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("Filename cannot be empty");
        }
    }
}
