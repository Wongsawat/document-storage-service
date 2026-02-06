package com.wpanther.storage.infrastructure.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event published when PDF signing is completed by pdf-signing-service.
 *
 * This event contains the signed PDF URL and signing metadata.
 * Consumed by document-storage-service to store the signed PDF.
 *
 * Extends IntegrationEvent for consistent event metadata across services.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfSignedEvent extends IntegrationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    // Invoice identifiers
    private String invoiceId;
    private String invoiceNumber;
    private String documentType;

    // Signed document details
    private String signedDocumentId;
    private String signedPdfUrl;
    private Long signedPdfSize;

    // Signing metadata
    private String transactionId;
    private String certificate;
    private String signatureLevel;
    private LocalDateTime signatureTimestamp;
}
