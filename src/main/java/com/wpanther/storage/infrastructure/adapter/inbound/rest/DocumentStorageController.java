package com.wpanther.storage.infrastructure.adapter.inbound.rest;

import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.port.inbound.DocumentStorageUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST adapter for document storage operations.
 * Implements the inbound REST endpoint for DocumentStorageUseCase.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentStorageController {

    private final DocumentStorageUseCase documentStorageUseCase;

    /**
     * Upload document
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDocument(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "documentType", required = false, defaultValue = "OTHER") DocumentType documentType,
        @RequestParam(value = "invoiceId", required = false) String invoiceId,
        @RequestParam(value = "invoiceNumber", required = false) String invoiceNumber
    ) {
        try {
            log.info("Received file upload: {} (size: {} bytes)", file.getOriginalFilename(), file.getSize());

            StoredDocument document = documentStorageUseCase.storeDocument(
                file.getBytes(),
                file.getOriginalFilename(),
                file.getContentType(),
                documentType,
                invoiceId,
                invoiceNumber
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "documentId", document.getId(),
                "fileName", document.getFileName(),
                "url", document.getStorageUrl(),
                "fileSize", document.getFileSize(),
                "checksum", document.getChecksum()
            ));

        } catch (Exception e) {
            log.error("Error uploading document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Failed to upload document",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Download document
     */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> downloadDocument(@PathVariable String id) {
        try {
            StoredDocument document = documentStorageUseCase.getDocument(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
            byte[] content = documentStorageUseCase.getDocumentContent(id);

            ByteArrayResource resource = new ByteArrayResource(content);

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .contentLength(content.length)
                .body(resource);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error downloading document: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get document metadata
     */
    @GetMapping("/{id}/metadata")
    public ResponseEntity<Map<String, Object>> getDocumentMetadata(@PathVariable String id) {
        try {
            StoredDocument document = documentStorageUseCase.getDocument(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

            return ResponseEntity.ok(Map.of(
                "id", document.getId(),
                "fileName", document.getFileName(),
                "contentType", document.getContentType(),
                "fileSize", document.getFileSize(),
                "checksum", document.getChecksum(),
                "documentType", document.getDocumentType().name(),
                "createdAt", document.getCreatedAt().toString(),
                "invoiceId", document.getInvoiceId() != null ? document.getInvoiceId() : "",
                "invoiceNumber", document.getInvoiceNumber() != null ? document.getInvoiceNumber() : ""
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete document
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) {
        try {
            documentStorageUseCase.deleteDocument(id);
            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error deleting document: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get documents by invoice ID
     */
    @GetMapping("/invoice/{invoiceId}")
    public ResponseEntity<List<Map<String, Object>>> getDocumentsByInvoiceId(@PathVariable String invoiceId) {
        List<StoredDocument> documents = documentStorageUseCase.getDocumentsByInvoice(invoiceId);

        List<Map<String, Object>> response = documents.stream()
            .map(doc -> Map.<String, Object>of(
                "id", doc.getId(),
                "fileName", doc.getFileName(),
                "url", doc.getStorageUrl(),
                "fileSize", doc.getFileSize(),
                "documentType", doc.getDocumentType().name(),
                "createdAt", doc.getCreatedAt().toString()
            ))
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}
