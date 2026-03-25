package com.wpanther.storage.infrastructure.adapter.in.rest;

import com.wpanther.storage.domain.exception.DocumentNotFoundException;
import com.wpanther.storage.domain.exception.InvalidDocumentException;
import com.wpanther.storage.domain.exception.StorageFailedException;
import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.application.usecase.DocumentStorageUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST adapter for document storage operations.
 * Implements the inbound REST endpoint for DocumentStorageUseCase.
 */
@Tag(name = "Documents", description = "Document storage and retrieval API")
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentStorageController {

    private final DocumentStorageUseCase documentStorageUseCase;

    /**
     * Upload document
     */
    @Operation(
        summary = "Upload a new document",
        description = "Stores a document file with optional invoice association. Returns document ID and storage metadata."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Document uploaded successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input (empty file, invalid format)"),
        @ApiResponse(responseCode = "500", description = "Storage operation failed")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDocument(
        @Parameter(description = "Document file to upload", required = true, schema = @Schema(type = "string", format = "binary"))
        @RequestParam("file") MultipartFile file,
        @Parameter(description = "Document type categorization", schema = @Schema(implementation = DocumentType.class))
        @RequestParam(value = "documentType", required = false, defaultValue = "OTHER") DocumentType documentType,
        @Parameter(description = "Associated invoice ID (1-100 alphanumeric characters)")
        @RequestParam(value = "invoiceId", required = false) String invoiceId,
        @Parameter(description = "Human-readable invoice number")
        @RequestParam(value = "invoiceNumber", required = false) String invoiceNumber
    ) {
        try {
            // Validate inputs
            DocumentValidator.validateFile(file);
            if (StringUtils.hasText(invoiceId)) {
                DocumentValidator.validateInvoiceId(invoiceId);
            }
            if (StringUtils.hasText(invoiceNumber)) {
                DocumentValidator.validateInvoiceNumber(invoiceNumber);
            }

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

        } catch (InvalidDocumentException e) {
            log.error("Invalid document upload", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Invalid document",
                "message", e.getMessage()
            ));
        } catch (StorageFailedException e) {
            log.error("Storage failed during upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Storage operation failed",
                "message", e.getMessage()
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for document upload: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Invalid input",
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Unexpected error uploading document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Failed to upload document",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Download document
     */
    @Operation(
        summary = "Download document content",
        description = "Returns the document file content as a downloadable attachment."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Document content returned", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid document ID format"),
        @ApiResponse(responseCode = "404", description = "Document not found"),
        @ApiResponse(responseCode = "500", description = "Storage operation failed")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Resource> downloadDocument(
        @Parameter(description = "Document UUID", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable String id) {
        try {
            // Validate document ID format
            DocumentValidator.validateDocumentId(id);

            StoredDocument document = documentStorageUseCase.getDocument(id)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + id));
            InputStream content = documentStorageUseCase.getDocumentContentStream(id);

            InputStreamResource resource = new InputStreamResource(content);

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .contentLength(document.getFileSize())
                .body(resource);

        } catch (DocumentNotFoundException e) {
            log.warn("Document not found: {}", id);
            return ResponseEntity.notFound().build();
        } catch (StorageFailedException e) {
            log.error("Storage failed while retrieving document: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid document ID format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Unexpected error downloading document: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get document metadata
     */
    @Operation(
        summary = "Get document metadata",
        description = "Returns document metadata without the file content."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Document metadata returned"),
        @ApiResponse(responseCode = "400", description = "Invalid document ID format"),
        @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @GetMapping("/{id}/metadata")
    public ResponseEntity<Map<String, Object>> getDocumentMetadata(
        @Parameter(description = "Document UUID", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable String id) {
        try {
            // Validate document ID format
            DocumentValidator.validateDocumentId(id);

            StoredDocument document = documentStorageUseCase.getDocument(id)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + id));

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

        } catch (DocumentNotFoundException e) {
            log.warn("Document not found: {}", id);
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid document ID format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Delete document
     */
    @Operation(
        summary = "Delete a document",
        description = "Deletes both the metadata and the physical file from storage."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Document deleted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid document ID format"),
        @ApiResponse(responseCode = "404", description = "Document not found"),
        @ApiResponse(responseCode = "500", description = "Storage operation failed")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
        @Parameter(description = "Document UUID", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable String id) {
        try {
            // Validate document ID format
            DocumentValidator.validateDocumentId(id);

            documentStorageUseCase.deleteDocument(id);
            return ResponseEntity.noContent().build();

        } catch (DocumentNotFoundException e) {
            log.warn("Document not found for deletion: {}", id);
            return ResponseEntity.notFound().build();
        } catch (StorageFailedException e) {
            log.error("Storage failed while deleting document: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid document ID format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Unexpected error deleting document: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get documents by invoice ID
     */
    @Operation(
        summary = "Get all documents for an invoice",
        description = "Returns all documents (PDF, XML, etc.) associated with a specific invoice ID."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of documents returned"),
        @ApiResponse(responseCode = "400", description = "Invalid invoice ID format")
    })
    @GetMapping("/invoice/{invoiceId}")
    public ResponseEntity<List<Map<String, Object>>> getDocumentsByInvoiceId(
        @Parameter(description = "Invoice ID (1-100 alphanumeric characters)", required = true, example = "INV-2024-001")
        @PathVariable String invoiceId) {
        try {
            // Validate invoice ID format
            DocumentValidator.validateInvoiceId(invoiceId);

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

        } catch (IllegalArgumentException e) {
            log.warn("Invalid invoice ID format: {}", invoiceId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}
