# Hexagonal Architecture Migration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Migrate Document Storage Service from DDD layered architecture to hexagonal architecture (ports and adapters pattern)

**Architecture:** Classic hexagonal with ports as interfaces in domain layer, inbound adapters call domain, outbound adapters are called by domain via ports. All dependencies point inward toward domain.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Apache Camel 4.14.4, MongoDB, PostgreSQL, JUnit 5, Mockito, Testcontainers

---

## Overview

This implementation plan migrates the document-storage-service from its current DDD layered structure to hexagonal architecture in 7 phases. The migration follows the "big bang" approach - complete reorganization in one effort.

**Key Changes:**
- `domain/port/` added - inbound and outbound port interfaces
- `application/` removed - use cases become domain ports
- `infrastructure/` reorganized into `infrastructure/adapter/` subdirectories
- All dependencies point inward toward domain

**Order of Operations:**
1. Create domain structure (ports, services, exceptions)
2. Implement domain services (core business logic)
3. Reorganize infrastructure adapters
4. Create inbound adapters (controllers, messaging, security)
5. Update configuration
6. Remove old `application/` structure
7. Update tests

---

## Phase 1: Create New Domain Structure

### Task 1: Create Domain Port Packages

**Files:**
- Create: `src/main/java/com/wpanther/storage/domain/port/inbound/` (directory)
- Create: `src/main/java/com/wpanther/storage/domain/port/outbound/` (directory)
- Create: `src/main/java/com/wpanther/storage/domain/service/` (directory)
- Create: `src/main/java/com/wpanther/storage/domain/exception/` (directory)

**Step 1: Create directory structure**

Run: `mkdir -p src/main/java/com/wpanther/storage/domain/port/inbound`
Run: `mkdir -p src/main/java/com/wpanther/storage/domain/port/outbound`
Run: `mkdir -p src/main/java/com/wpanther/storage/domain/service`
Run: `mkdir -p src/main/java/com/wpanther/storage/domain/exception`

**Step 2: Verify directories created**

Run: `ls -la src/main/java/com/wpanther/storage/domain/`
Expected: List shows `port/`, `service/`, `exception/`, `model/`, `event/`

**Step 3: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/
git commit -m "feat: create domain port, service, and exception directories"
```

---

### Task 2: Create StorageResult Value Object

**Files:**
- Create: `src/main/java/com/wpanther/storage/domain/model/StorageResult.java`

**Step 1: Write the value object**

Create `src/main/java/com/wpanther/storage/domain/model/StorageResult.java`:

```java
package com.wpanther.storage.domain.model;

import java.time.Instant;

/**
 * Value object representing the result of a storage operation.
 */
public record StorageResult(
    String location,
    String provider,
    Instant timestamp
) {
    public static StorageResult success(String location, String provider) {
        return new StorageResult(location, provider, Instant.now());
    }
}
```

**Step 2: Create test file**

Create `src/test/java/com/wpanther/storage/domain/model/StorageResultTest.java`:

```java
package com.wpanther.storage.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StorageResultTest {

    @Test
    void storageResult_createsSuccessfully() {
        StorageResult result = new StorageResult("/path/to/doc", "local", Instant.now());

        assertThat(result.location()).isEqualTo("/path/to/doc");
        assertThat(result.provider()).isEqualTo("local");
        assertThat(result.timestamp()).isNotNull();
    }

    @Test
    void successFactoryMethod_createsResultWithTimestamp() {
        StorageResult result = StorageResult.success("/path/to/doc", "local");

        assertThat(result.location()).isEqualTo("/path/to/doc");
        assertThat(result.provider()).isEqualTo("local");
        assertThat(result.timestamp()).isNotNull();
        assertThat(result.timestamp()).isBeforeOrEqualTo(Instant.now());
    }
}
```

**Step 3: Run test to verify it passes**

Run: `mvn test -Dtest=StorageResultTest -q`
Expected: Tests PASS

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/model/StorageResult.java
git add src/test/java/com/wpanther/storage/domain/model/StorageResultTest.java
git commit -m "feat: add StorageResult value object"
```

---

### Task 3: Create DomainException Base Class

**Files:**
- Create: `src/main/java/com/wpanther/storage/domain/exception/DomainException.java`

**Step 1: Write the exception class**

Create `src/main/java/com/wpanther/storage/domain/exception/DomainException.java`:

```java
package com.wpanther.storage.domain.exception;

/**
 * Base class for all domain exceptions.
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/exception/DomainException.java
git commit -m "feat: add DomainException base class"
```

---

### Task 4: Create DocumentNotFoundException

**Files:**
- Create: `src/main/java/com/wpanther/storage/domain/exception/DocumentNotFoundException.java`
- Create: `src/test/java/com/wpanther/storage/domain/exception/DocumentNotFoundExceptionTest.java`

**Step 1: Write the exception class**

Create `src/main/java/com/wpanther/storage/domain/exception/DocumentNotFoundException.java`:

```java
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
```

**Step 2: Write the test**

Create `src/test/java/com/wpanther/storage/domain/exception/DocumentNotFoundExceptionTest.java`:

```java
package com.wpanther.storage.domain.exception;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DocumentNotFoundExceptionTest {

    @Test
    void exception_containsDocumentId() {
        DocumentNotFoundException ex = new DocumentNotFoundException("doc-123");

        assertThat(ex.getMessage()).contains("doc-123");
        assertThat(ex.documentId()).isEqualTo("doc-123");
    }
}
```

**Step 3: Run test**

Run: `mvn test -Dtest=DocumentNotFoundExceptionTest -q`
Expected: PASS

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/exception/DocumentNotFoundException.java
git add src/test/java/com/wpanther/storage/domain/exception/DocumentNotFoundExceptionTest.java
git commit -m "feat: add DocumentNotFoundException"
```

---

### Task 5: Create StorageFailedException

**Files:**
- Create: `src/main/java/com/wpanther/storage/domain/exception/StorageFailedException.java`
- Create: `src/test/java/com/wpanther/storage/domain/exception/StorageFailedExceptionTest.java`

**Step 1: Write the exception class**

Create `src/main/java/com/wpanther/storage/domain/exception/StorageFailedException.java`:

```java
package com.wpanther.storage.domain.exception;

public class StorageFailedException extends DomainException {

    public StorageFailedException(String reason) {
        super("Storage operation failed: " + reason);
    }

    public StorageFailedException(String reason, Throwable cause) {
        super("Storage operation failed: " + reason, cause);
    }
}
```

**Step 2: Write the test**

Create `src/test/java/com/wpanther/storage/domain/exception/StorageFailedExceptionTest.java`:

```java
package com.wpanther.storage.domain.exception;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StorageFailedExceptionTest {

    @Test
    void exception_containsReason() {
        StorageFailedException ex = new StorageFailedException("disk full");

        assertThat(ex.getMessage()).contains("disk full");
    }

    @Test
    void exceptionWithCause_containsCause() {
        Throwable cause = new RuntimeException("IO error");
        StorageFailedException ex = new StorageFailedException("disk full", cause);

        assertThat(ex.getCause()).isSameAs(cause);
    }
}
```

**Step 3: Run test**

Run: `mvn test -Dtest=StorageFailedExceptionTest -q`
Expected: PASS

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/exception/StorageFailedException.java
git add src/test/java/com/wpanther/storage/domain/exception/StorageFailedExceptionTest.java
git commit -m "feat: add StorageFailedException"
```

---

### Task 6: Create InvalidDocumentException

**Files:**
- Create: `src/main/java/com/wpanther/storage/domain/exception/InvalidDocumentException.java`
- Create: `src/test/java/com/wpanther/storage/domain/exception/InvalidDocumentExceptionTest.java`

**Step 1: Write the exception class**

Create `src/main/java/com/wpanther/storage/domain/exception/InvalidDocumentException.java`:

```java
package com.wpanther.storage.domain.exception;

public class InvalidDocumentException extends DomainException {

    public InvalidDocumentException(String message) {
        super("Invalid document: " + message);
    }

    public InvalidDocumentException(String message, Throwable cause) {
        super("Invalid document: " + message, cause);
    }
}
```

**Step 2: Write the test**

Create `src/test/java/com/wpanther/storage/domain/exception/InvalidDocumentExceptionTest.java`:

```java
package com.wpanther/storage.domain.exception;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InvalidDocumentExceptionTest {

    @Test
    void exception_containsMessage() {
        InvalidDocumentException ex = new InvalidDocumentException("empty content");

        assertThat(ex.getMessage()).contains("empty content");
    }

    @Test
    void exceptionWithCause_containsCause() {
        Throwable cause = new RuntimeException("validation error");
        InvalidDocumentException ex = new InvalidDocumentException("invalid format", cause);

        assertThat(ex.getCause()).isSameAs(cause);
    }
}
```

**Step 3: Run test**

Run: `mvn test -Dtest=InvalidDocumentExceptionTest -q`
Expected: PASS

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/exception/InvalidDocumentException.java
git add src/test/java/com/wpanther/storage/domain/exception/InvalidDocumentExceptionTest.java
git commit -m "feat: add InvalidDocumentException"
```

---

### Task 7: Create StorageException (Infrastructure)

**Files:**
- Create: `src/main/java/com/wpanther/storage/domain/model/StorageException.java`

**Step 1: Write the exception class**

Create `src/main/java/com/wpanther/storage/domain/model/StorageException.java`:

```java
package com.wpanther.storage.domain.model;

/**
 * Exception thrown by storage adapters when storage operations fail.
 * This is converted to StorageFailedException by domain services.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/model/StorageException.java
git commit -m "feat: add StorageException for infrastructure adapters"
```

---

## Phase 2: Define Port Interfaces

### Task 8: Create StorageProviderPort Interface

**Files:**
- Create: `src/main/java/com/wpanther/storage/domain/port/outbound/StorageProviderPort.java`

**Step 1: Write the port interface**

Create `src/main/java/com/wpanther/storage/domain/port/outbound/StorageProviderPort.java`:

```java
package com.wpanther.storage.domain.port.outbound;

import com.wpanther.storage.domain.model.StorageResult;
import com.wpanther.storage.domain.model.StorageException;

import java.io.InputStream;

/**
 * Outbound port for file storage operations.
 * Implemented by LocalFileStorageAdapter and S3FileStorageAdapter.
 */
public interface StorageProviderPort {

    /**
     * Store a document and return the storage location.
     * @param documentId Unique document identifier
     * @param content Document content as input stream
     * @param originalFilename Original filename (used for extension)
     * @param size Content size in bytes
     * @return StorageResult with location and provider info
     * @throws StorageException if storage fails
     */
    StorageResult store(String documentId, InputStream content,
                        String originalFilename, long size) throws StorageException;

    /**
     * Retrieve a document from storage.
     * @param storageLocation Location returned by store()
     * @return InputStream with document content
     * @throws StorageException if retrieval fails
     */
    InputStream retrieve(String storageLocation) throws StorageException;

    /**
     * Delete a document from storage.
     * @param storageLocation Location returned by store()
     * @throws StorageException if deletion fails
     */
    void delete(String storageLocation) throws StorageException;

    /**
     * Check if a document exists at the given location.
     * @param storageLocation Location to check
     * @return true if exists, false otherwise
     */
    boolean exists(String storageLocation);
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/port/outbound/StorageProviderPort.java
git commit -m "feat: add StorageProviderPort interface"
```

---

### Task 9: Create DocumentRepositoryPort Interface

**Files:**
- Create: `src/main/java/com/wpanther/storage/domain/port/outbound/DocumentRepositoryPort.java`

**Step 1: Write the port interface**

Create `src/main/java/com/wpanther/storage/domain/port/outbound/DocumentRepositoryPort.java`:

```java
package com.wpanther.storage.domain.port.outbound;

import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.model.DocumentType;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for document metadata persistence.
 * Implemented by MongoDocumentAdapter.
 */
public interface DocumentRepositoryPort {

    /**
     * Save a document (create or update).
     * @param document Document to save
     * @return Saved document with generated ID if new
     */
    StoredDocument save(StoredDocument document);

    /**
     * Find a document by ID.
     * @param id Document ID
     * @return Optional containing the document, or empty if not found
     */
    Optional<StoredDocument> findById(String id);

    /**
     * Find all documents for a given invoice ID.
     * @param invoiceId Invoice ID
     * @return List of documents for the invoice
     */
    List<StoredDocument> findByInvoiceId(String invoiceId);

    /**
     * Find a document by invoice ID and document type.
     * Used for idempotency checks in saga operations.
     * @param invoiceId Invoice ID
     * @param type Document type
     * @return Optional containing the document, or empty if not found
     */
    Optional<StoredDocument> findByInvoiceIdAndDocumentType(
            String invoiceId, DocumentType type);

    /**
     * Delete a document by ID.
     * @param id Document ID
     */
    void deleteById(String id);

    /**
     * Check if a document exists for the given invoice and type.
     * @param invoiceId Invoice ID
     * @param type Document type
     * @return true if exists, false otherwise
     */
    boolean existsByInvoiceIdAndDocumentType(String invoiceId, DocumentType type);
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/port/outbound/DocumentRepositoryPort.java
git commit -m "feat: add DocumentRepositoryPort interface"
```

---

### Task 10: Create OutboxRepositoryPort Interface

**Files:**
- Create: `src/main/java/com/wpanther/storage/domain/port/outbound/OutboxRepositoryPort.java`

**Step 1: Write the port interface**

Create `src/main/java/com/wpanther/storage/domain/port/outbound/OutboxRepositoryPort.java`:

```java
package com.wpanther.storage.domain.port.outbound;

import com.wpanther.saga.commons.outbox.OutboxEvent;

/**
 * Outbound port for outbox event persistence.
 * Implemented by JpaOutboxAdapter.
 */
public interface OutboxRepositoryPort {

    /**
     * Save an outbox event.
     * @param event Event to save
     * @return Saved event with generated ID
     */
    OutboxEvent save(OutboxEvent event);

    /**
     * Delete an outbox event by ID.
     * @param id Event ID
     */
    void deleteById(String id);
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/port/outbound/OutboxRepositoryPort.java
git commit -m "feat: add OutboxRepositoryPort interface"
```

---

### Task 11: Create MessagePublisherPort Interface

**Files:**
- Create: `src/main/java/com/wpanther/storage/domain/port/outbound/MessagePublisherPort.java`

**Step 1: Write the port interface**

Create `src/main/java/com/wpanther/storage/domain/port/outbound/MessagePublisherPort.java`:

```java
package com.wpanther.storage.domain.port.outbound;

import com.wpanther.storage.domain.event.DocumentStoredEvent;
import com.wpanther.storage.domain.event.DocumentStorageReplyEvent;
import com.wpanther.storage.domain.event.SignedXmlStorageReplyEvent;
import com.wpanther.storage.domain.event.PdfStorageReplyEvent;

/**
 * Outbound port for publishing messages to Kafka.
 * Implemented by MessagePublisherAdapter using the outbox pattern.
 */
public interface MessagePublisherPort {

    /**
     * Publish a document stored event.
     * @param event Event to publish
     */
    void publishEvent(DocumentStoredEvent event);

    /**
     * Publish a document storage saga reply.
     * @param reply Reply to publish
     */
    void publishReply(DocumentStorageReplyEvent reply);

    /**
     * Publish a signed XML storage saga reply.
     * @param reply Reply to publish
     */
    void publishReply(SignedXmlStorageReplyEvent reply);

    /**
     * Publish a PDF storage saga reply.
     * @param reply Reply to publish
     */
    void publishReply(PdfStorageReplyEvent reply);
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/port/outbound/MessagePublisherPort.java
git commit -m "feat: add MessagePublisherPort interface"
```

---

### Task 12: Create DocumentStorageUseCase Interface

**Files:**
- Create: `src/main/java/com/wpanther/storage/domain/port/inbound/DocumentStorageUseCase.java`

**Step 1: Write the use case interface**

Create `src/main/java/com/wpanther/storage/domain/port/inbound/DocumentStorageUseCase.java`:

```java
package com.wpanther.storage.domain.port.inbound;

import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.model.DocumentType;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for document storage operations.
 * Implemented by FileStorageDomainService.
 * Called by DocumentStorageController (REST adapter).
 */
public interface DocumentStorageUseCase {

    /**
     * Store a document.
     * @param content Document content as byte array
     * @param filename Original filename
     * @param type Document type
     * @param invoiceId Associated invoice ID
     * @return Stored document metadata
     */
    StoredDocument storeDocument(byte[] content, String filename,
                                 DocumentType type, String invoiceId);

    /**
     * Get a document by ID.
     * @param documentId Document ID
     * @return Optional containing the document, or empty if not found
     */
    Optional<StoredDocument> getDocument(String documentId);

    /**
     * Get all documents for an invoice.
     * @param invoiceId Invoice ID
     * @return List of documents
     */
    List<StoredDocument> getDocumentsByInvoice(String invoiceId);

    /**
     * Delete a document by ID.
     * @param documentId Document ID
     * @throws com.wpanther.storage.domain.exception.DocumentNotFoundException if not found
     */
    void deleteDocument(String documentId);

    /**
     * Check if a document exists for the given invoice and type.
     * Used for idempotency checks in saga operations.
     * @param invoiceId Invoice ID
     * @param type Document type
     * @return true if exists, false otherwise
     */
    boolean existsByInvoiceAndType(String invoiceId, DocumentType type);
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/port/inbound/DocumentStorageUseCase.java
git commit -m "feat: add DocumentStorageUseCase interface"
```

---

### Task 13: Create SagaCommandUseCase Interface

**Files:**
- Create: `src/main/java/com/wpanther/storage/domain/port/inbound/SagaCommandUseCase.java`

**Step 1: Write the use case interface**

Create `src/main/java/com/wpanther/storage/domain/port/inbound/SagaCommandUseCase.java`:

```java
package com.wpanther.storage.domain.port.inbound;

import com.wpanther.storage.domain.event.*;

/**
 * Inbound port for saga command handling.
 * Implemented by SagaOrchestrationService.
 * Called by SagaCommandAdapter (Kafka/Camel adapter).
 */
public interface SagaCommandUseCase {

    /**
     * Handle process document storage command.
     * Step 8 of saga: store signed PDF document.
     * @param command Process command
     */
    void handleProcessCommand(ProcessDocumentStorageCommand command);

    /**
     * Handle process signed XML storage command.
     * @param command Process command
     */
    void handleProcessCommand(ProcessSignedXmlStorageCommand command);

    /**
     * Handle process PDF storage command.
     * Step 6 of saga: store unsigned PDF from MinIO.
     * @param command Process command (contains pdfUrl and pdfSize)
     */
    void handleProcessCommand(ProcessPdfStorageCommand command);

    /**
     * Handle compensate document storage command.
     * @param command Compensation command
     */
    void handleCompensation(CompensateDocumentStorageCommand command);

    /**
     * Handle compensate signed XML storage command.
     * @param command Compensation command
     */
    void handleCompensation(CompensateSignedXmlStorageCommand command);

    /**
     * Handle compensate PDF storage command.
     * @param command Compensation command
     */
    void handleCompensation(CompensatePdfStorageCommand command);
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/port/inbound/SagaCommandUseCase.java
git commit -m "feat: add SagaCommandUseCase interface"
```

---

### Task 14: Create AuthenticationUseCase Interface

**Files:**
- Create: `src/main/java/com/wpanther/storage/domain/port/inbound/AuthenticationUseCase.java`
- Create: `src/main/java/com/wpanther/storage/domain/model/AuthToken.java`

**Step 1: Write the AuthToken value object**

Create `src/main/java/com/wpanther/storage/domain/model/AuthToken.java`:

```java
package com.wpanther.storage.domain.model;

import java.time.Instant;

/**
 * Value object representing an authentication token.
 */
public record AuthToken(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    Instant issuedAt
) {
    public static AuthToken of(String accessToken, String refreshToken, long expiresIn) {
        return new AuthToken(
            accessToken,
            refreshToken,
            "Bearer",
            expiresIn,
            Instant.now()
        );
    }
}
```

**Step 2: Write the use case interface**

Create `src/main/java/com/wpanther/storage/domain/port/inbound/AuthenticationUseCase.java`:

```java
package com.wpanther.storage.domain.port.inbound;

import com.wpanther.storage.domain.model.AuthToken;

/**
 * Inbound port for authentication operations.
 * Implemented by AuthenticationDomainService.
 * Called by AuthenticationController and JwtAuthenticationAdapter.
 */
public interface AuthenticationUseCase {

    /**
     * Authenticate a user and generate tokens.
     * @param username Username
     * @param password Password
     * @return AuthToken with access and refresh tokens
     */
    AuthToken authenticate(String username, String password);

    /**
     * Refresh an access token using a refresh token.
     * @param refreshToken Refresh token
     * @return New AuthToken
     */
    AuthToken refreshToken(String refreshToken);

    /**
     * Logout a user (invalidate token).
     * @param token Access token to invalidate
     */
    void logout(String token);

    /**
     * Validate a token and extract username.
     * @param token JWT token
     * @return true if valid, false otherwise
     */
    boolean validateToken(String token);

    /**
     * Extract username from a valid token.
     * @param token JWT token
     * @return Username
     */
    String extractUsername(String token);
}
```

**Step 3: Create test for AuthToken**

Create `src/test/java/com/wpanther/storage/domain/model/AuthTokenTest.java`:

```java
package com.wpanther.storage.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthTokenTest {

    @Test
    void authToken_createsSuccessfully() {
        AuthToken token = new AuthToken(
            "access-token",
            "refresh-token",
            "Bearer",
            3600,
            Instant.now()
        );

        assertThat(token.accessToken()).isEqualTo("access-token");
        assertThat(token.refreshToken()).isEqualTo("refresh-token");
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(3600);
    }

    @Test
    void factoryMethod_createsToken() {
        AuthToken token = AuthToken.of("access", "refresh", 3600);

        assertThat(token.accessToken()).isEqualTo("access");
        assertThat.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(3600);
        assertThat(token.issuedAt()).isNotNull();
    }
}
```

**Step 4: Run test**

Run: `mvn test -Dtest=AuthTokenTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/model/AuthToken.java
git add src/main/java/com/wpanther/storage/domain/port/inbound/AuthenticationUseCase.java
git add src/test/java/com/wpanther/storage/domain/model/AuthTokenTest.java
git commit -m "feat: add AuthToken and AuthenticationUseCase"
```

---

## Phase 3: Implement Domain Services

### Task 15: Create FileStorageDomainService - Setup

**Files:**
- Create: `src/main/java/com/wpanther/storage/domain/service/FileStorageDomainService.java`
- Create: `src/test/java/com/wpanther/storage/domain/service/FileStorageDomainServiceTest.java`

**Step 1: Write the service skeleton with tests**

First, create the test file `src/test/java/com/wpanther/storage/domain/service/FileStorageDomainServiceTest.java`:

```java
package com.wpanther.storage.domain.service;

import com.wpanther.storage.domain.model.*;
import com.wpanther.storage.domain.port.outbound.*;
import com.wpanther.storage.domain.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileStorageDomainServiceTest {

    @Mock
    private StorageProviderPort storageProvider;

    @Mock
    private DocumentRepositoryPort documentRepository;

    @InjectMocks
    private FileStorageDomainService service;

    @Test
    void storeDocument_success() {
        // Given
        byte[] content = "test content".getBytes();
        String filename = "test.pdf";
        DocumentType type = DocumentType.INVOICE_PDF;
        String invoiceId = "INV-001";

        StorageResult storageResult = StorageResult.success("/2024/03/04/uuid.pdf", "local");
        when(storageProvider.store(anyString(), any(InputStream.class), anyString(), anyLong()))
            .thenReturn(storageResult);
        when(documentRepository.save(any(StoredDocument.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        StoredDocument result = service.store(content, filename, type, invoiceId);

        // Then
        assertThat(result.documentType()).isEqualTo(type);
        assertThat(result.invoiceId()).isEqualTo(invoiceId);
        assertThat(result.originalFilename()).isEqualTo(filename);
        assertThat(result.storageLocation()).isEqualTo("/2024/03/04/uuid.pdf");
        assertThat(result.storageProvider()).isEqualTo("local");
        assertThat(result.fileSize()).isEqualTo(content.length);

        verify(storageProvider).store(anyString(), any(InputStream.class), eq(filename), eq((long) content.length));
        verify(documentRepository).save(any(StoredDocument.class));
    }

    @Test
    void storeDocument_emptyContent_throwsException() {
        // Given
        byte[] empty = new byte[0];

        // When/Then
        assertThatThrownBy(() -> service.store(empty, "test.pdf", DocumentType.INVOICE_PDF, "INV-001"))
            .isInstanceOf(InvalidDocumentException.class)
            .hasMessageContaining("cannot be empty");

        verify(storageProvider, never()).store(any(), any(), any(), anyLong());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void storeDocument_nullContent_throwsException() {
        // When/Then
        assertThatThrownBy(() -> service.store(null, "test.pdf", DocumentType.INVOICE_PDF, "INV-001"))
            .isInstanceOf(InvalidDocumentException.class)
            .hasMessageContaining("cannot be empty");

        verify(storageProvider, never()).store(any(), any(), any(), anyLong());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void getDocument_found_returnsDocument() {
        // Given
        String documentId = "doc-123";
        StoredDocument doc = StoredDocument.builder()
            .id(documentId)
            .invoiceId("INV-001")
            .documentType(DocumentType.INVOICE_PDF)
            .build();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));

        // When
        Optional<StoredDocument> result = service.retrieve(documentId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(documentId);
    }

    @Test
    void getDocument_notFound_returnsEmpty() {
        // Given
        when(documentRepository.findById("doc-999")).thenReturn(Optional.empty());

        // When
        Optional<StoredDocument> result = service.retrieve("doc-999");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void findByInvoice_returnsDocuments() {
        // Given
        String invoiceId = "INV-001";
        StoredDocument doc1 = StoredDocument.builder().id("doc-1").invoiceId(invoiceId).build();
        StoredDocument doc2 = StoredDocument.builder().id("doc-2").invoiceId(invoiceId).build();
        when(documentRepository.findByInvoiceId(invoiceId)).thenReturn(List.of(doc1, doc2));

        // When
        java.util.List<StoredDocument> result = service.findByInvoice(invoiceId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo("doc-1");
        assertThat(result.get(1).id()).isEqualTo("doc-2");
    }

    @Test
    void deleteDocument_success() {
        // Given
        String documentId = "doc-123";
        StoredDocument doc = StoredDocument.builder()
            .id(documentId)
            .storageLocation("/path/to/doc")
            .build();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));
        doNothing().when(storageProvider).delete("/path/to/doc");
        doNothing().when(documentRepository).deleteById(documentId);

        // When
        service.delete(documentId);

        // Then
        verify(storageProvider).delete("/path/to/doc");
        verify(documentRepository).deleteById(documentId);
    }

    @Test
    void deleteDocument_notFound_throwsException() {
        // Given
        when(documentRepository.findById("doc-999")).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> service.delete("doc-999"))
            .isInstanceOf(DocumentNotFoundException.class);

        verify(storageProvider, never()).delete(anyString());
        verify(documentRepository, never()).deleteById(anyString());
    }

    @Test
    void existsByInvoiceAndType_returnsTrue() {
        // Given
        when(documentRepository.existsByInvoiceIdAndDocumentType("INV-001", DocumentType.INVOICE_PDF))
            .thenReturn(true);

        // When
        boolean result = service.existsByInvoiceAndType("INV-001", DocumentType.INVOICE_PDF);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void existsByInvoiceAndType_returnsFalse() {
        // Given
        when(documentRepository.existsByInvoiceIdAndDocumentType("INV-001", DocumentType.INVOICE_PDF))
            .thenReturn(false);

        // When
        boolean result = service.existsByInvoiceAndType("INV-001", DocumentType.INVOICE_PDF);

        // Then
        assertThat(result).isFalse();
    }
}
```

**Step 2: Write the service implementation**

Create `src/main/java/com/wpanther/storage/domain/service/FileStorageDomainService.java`:

```java
package com.wpanther.storage.domain.service;

import com.wpanther.storage.domain.model.*;
import com.wpanther.storage.domain.port.outbound.*;
import com.wpanther.storage.domain.exception.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain service for document storage operations.
 * Implements DocumentStorageUseCase port.
 */
@Service
public class FileStorageDomainService implements com.wpanther.storage.domain.port.inbound.DocumentStorageUseCase {

    private final StorageProviderPort storageProvider;
    private final DocumentRepositoryPort documentRepository;

    public FileStorageDomainService(StorageProviderPort storageProvider,
                                     DocumentRepositoryPort documentRepository) {
        this.storageProvider = storageProvider;
        this.documentRepository = documentRepository;
    }

    @Override
    @Transactional
    public StoredDocument store(byte[] content, String filename,
                                DocumentType type, String invoiceId) {
        // 1. Validate input
        if (content == null || content.length == 0) {
            throw new InvalidDocumentException("Document content cannot be empty");
        }

        // 2. Generate document ID
        String documentId = UUID.randomUUID().toString();

        // 3. Store file via provider
        InputStream inputStream = new ByteArrayInputStream(content);
        StorageResult result = storageProvider.store(documentId, inputStream, filename, content.length);

        // 4. Calculate checksum
        String checksum = DigestUtils.sha256Hex(content);

        // 5. Create domain model
        StoredDocument document = StoredDocument.builder()
            .id(documentId)
            .invoiceId(invoiceId)
            .documentType(type)
            .originalFilename(filename)
            .storageLocation(result.location())
            .storageProvider(result.provider())
            .fileSize(content.length)
            .checksum(checksum)
            .createdAt(Instant.now())
            .build();

        // 6. Persist metadata
        return documentRepository.save(document);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredDocument> retrieve(String documentId) {
        return documentRepository.findById(documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredDocument> findByInvoice(String invoiceId) {
        return documentRepository.findByInvoiceId(invoiceId);
    }

    @Override
    @Transactional
    public void delete(String documentId) {
        StoredDocument doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentNotFoundException(documentId));

        storageProvider.delete(doc.storageLocation());
        documentRepository.deleteById(documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByInvoiceAndType(String invoiceId, DocumentType type) {
        return documentRepository.existsByInvoiceIdAndDocumentType(invoiceId, type);
    }

    // Methods implementing DocumentStorageUseCase with renamed parameters

    @Override
    public StoredDocument storeDocument(byte[] content, String filename,
                                       DocumentType type, String invoiceId) {
        return store(content, filename, type, invoiceId);
    }

    @Override
    public Optional<StoredDocument> getDocument(String documentId) {
        return retrieve(documentId);
    }

    @Override
    public List<StoredDocument> getDocumentsByInvoice(String invoiceId) {
        return findByInvoice(invoiceId);
    }

    @Override
    public void deleteDocument(String documentId) {
        delete(documentId);
    }

    /**
     * Download content from storage location.
     * @param storageLocation Storage location
     * @return InputStream with document content
     * @throws StorageException if retrieval fails
     */
    public InputStream downloadContent(String storageLocation) {
        return storageProvider.retrieve(storageLocation);
    }
}
```

**Step 3: Run tests to verify they pass**

Run: `mvn test -Dtest=FileStorageDomainServiceTest -q`
Expected: All tests PASS

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/service/FileStorageDomainService.java
git add src/test/java/com/wpanther/storage/domain/service/FileStorageDomainServiceTest.java
git commit -m "feat: implement FileStorageDomainService with tests"
```

---

### Task 16: Create PdfDownloadDomainService

**Files:**
- Create: `src/main/java/com/wpanther/storage/domain/service/PdfDownloadDomainService.java`
- Create: `src/test/java/com/wpanther/storage/domain/service/PdfDownloadDomainServiceTest.java`

**Step 1: Write the test**

Create `src/test/java/com/wpanther/storage/domain/service/PdfDownloadDomainServiceTest.java`:

```java
package com.wpanther.storage.domain.service;

import com.wpanther.storage.domain.exception.StorageFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PdfDownloadDomainServiceTest {

    @InjectMocks
    private PdfDownloadDomainService service;

    @Test
    void downloadPdf_success() throws Exception {
        // This test will use mocking of HTTP client
        // For now, we'll create a simple test structure
        // Actual implementation will use a testable HTTP client design

        assertThat(service).isNotNull();
    }
}
```

**Step 2: Write the service implementation**

Create `src/main/java/com/wpanther/storage/domain/service/PdfDownloadDomainService.java`:

```java
package com.wpanther.storage.domain.service;

import com.wpanther.storage.domain.exception.StorageFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Domain service for downloading PDFs from external URLs.
 * Used by SagaOrchestrationService to download PDFs from MinIO.
 */
@Service
public class PdfDownloadDomainService {

    private static final Logger log = LoggerFactory.getLogger(PdfDownloadDomainService.class);
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int REQUEST_TIMEOUT_SECONDS = 60;

    private final HttpClient httpClient;

    public PdfDownloadDomainService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();
    }

    /**
     * Download a PDF from the given URL.
     * @param pdfUrl URL of the PDF to download
     * @return PDF content as byte array
     * @throws StorageFailedException if download fails
     */
    public byte[] downloadPdf(String pdfUrl) {
        log.info("Downloading PDF from: {}", pdfUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(pdfUrl))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build();

            HttpResponse<byte[]> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofByteArray()
            );

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Successfully downloaded PDF, size: {} bytes", response.body().length);
                return response.body();
            } else {
                throw new StorageFailedException(
                    "Failed to download PDF from " + pdfUrl +
                    ", HTTP status: " + response.statusCode()
                );
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageFailedException("Failed to download PDF from " + pdfUrl, e);
        }
    }
}
```

**Step 3: Run test**

Run: `mvn test -Dtest=PdfDownloadDomainServiceTest -q`
Expected: PASS (placeholder test)

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/service/PdfDownloadDomainService.java
git add src/test/java/com/wpanther/storage/domain/service/PdfDownloadDomainServiceTest.java
git commit -m "feat: add PdfDownloadDomainService for downloading PDFs from URLs"
```

---

### Task 17: Create SagaOrchestrationService - Setup

**Files:**
- Create: `src/main/java/com/wpanther/storage/domain/service/SagaOrchestrationService.java`
- Create: `src/test/java/com/wpanther/storage/domain/service/SagaOrchestrationServiceTest.java`

**Step 1: Write the test**

Create `src/test/java/com/wpanther/storage/domain/service/SagaOrchestrationServiceTest.java`:

```java
package com.wpanther.storage.domain.service;

import com.wpanther.storage.domain.event.*;
import com.wpanther.storage.domain.model.*;
import com.wpanther.storage.domain.port.outbound.*;
import com.wpanther.storage.domain.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaOrchestrationServiceTest {

    @Mock private FileStorageDomainService storageService;
    @Mock private PdfDownloadDomainService pdfDownloadService;
    @Mock private MessagePublisherPort messagePublisher;

    @InjectMocks
    private SagaOrchestrationService service;

    @Test
    void handleProcessDocumentCommand_success() {
        // Test implementation will be added in next tasks
        // For now, create placeholder
        assertThat(service).isNotNull();
    }

    @Test
    void handleCompensateDocumentCommand_success() {
        // Test implementation will be added
        assertThat(service).isNotNull();
    }
}
```

**Step 2: Write the service skeleton**

Create `src/main/java/com/wpanther/storage/domain/service/SagaOrchestrationService.java`:

```java
package com.wpanther.storage.domain.service;

import com.wpanther.storage.domain.event.*;
import com.wpanther.storage.domain.model.*;
import com.wpanther.storage.domain.port.outbound.*;
import com.wpanther.storage.domain.port.inbound.SagaCommandUseCase;
import com.wpanther.storage.domain.exception.*;
import com.wpanther.saga.commons.outbox.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Domain service for saga command orchestration.
 * Implements SagaCommandUseCase port.
 * Handles document storage commands from the orchestrator.
 */
@Service
public class SagaOrchestrationService implements SagaCommandUseCase {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrationService.class);

    private final FileStorageDomainService storageService;
    private final PdfDownloadDomainService pdfDownloadService;
    private final MessagePublisherPort messagePublisher;

    public SagaOrchestrationService(FileStorageDomainService storageService,
                                     PdfDownloadDomainService pdfDownloadService,
                                     MessagePublisherPort messagePublisher) {
        this.storageService = storageService;
        this.pdfDownloadService = pdfDownloadService;
        this.messagePublisher = messagePublisher;
    }

    // Implementation will be completed in subsequent tasks
    // Each saga command handler will be implemented separately

    @Override
    @Transactional
    public void handleProcessCommand(ProcessDocumentStorageCommand command) {
        // TODO: Implement in Task 18
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    @Transactional
    public void handleProcessCommand(ProcessSignedXmlStorageCommand command) {
        // TODO: Implement in Task 19
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    @Transactional
    public void handleProcessCommand(ProcessPdfStorageCommand command) {
        // TODO: Implement in Task 20
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    @Transactional
    public void handleCompensation(CompensateDocumentStorageCommand command) {
        // TODO: Implement in Task 21
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    @Transactional
    public void handleCompensation(CompensateSignedXmlStorageCommand command) {
        // TODO: Implement in Task 22
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    @Transactional
    public void handleCompensation(CompensatePdfStorageCommand command) {
        // TODO: Implement in Task 23
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
```

**Step 3: Run test**

Run: `mvn test -Dtest=SagaOrchestrationServiceTest -q`
Expected: PASS (placeholder tests)

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/service/SagaOrchestrationService.java
git add src/test/java/com/wpanther/storage/domain/service/SagaOrchestrationServiceTest.java
git commit -m "feat: add SagaOrchestrationService skeleton"
```

---

### Task 18: Implement ProcessDocumentStorageCommand Handler

**Files:**
- Modify: `src/main/java/com/wpanther/storage/domain/service/SagaOrchestrationService.java`
- Modify: `src/test/java/com/wpanther/storage/domain/service/SagaOrchestrationServiceTest.java`

**Step 1: Add test case**

Add to `SagaOrchestrationServiceTest.java`:

```java
    @Test
    void handleProcessDocumentCommand_success() {
        // Given
        ProcessDocumentStorageCommand command = new ProcessDocumentStorageCommand(
            "saga-123", "doc-123", "http://minio:9000/doc.pdf", 1024
        );
        byte[] pdfContent = "test pdf content".getBytes();
        StoredDocument storedDoc = StoredDocument.builder()
            .id("stored-123")
            .invoiceId("doc-123")
            .documentType(DocumentType.INVOICE_PDF)
            .build();

        when(storageService.existsByInvoiceAndType("doc-123", DocumentType.INVOICE_PDF))
            .thenReturn(false);
        when(pdfDownloadService.downloadPdf(command.documentUrl()))
            .thenReturn(pdfContent);
        when(storageService.store(eq(pdfContent), anyString(), eq(DocumentType.INVOICE_PDF), eq("doc-123")))
            .thenReturn(storedDoc);
        doNothing().when(messagePublisher).publishEvent(any(DocumentStoredEvent.class));
        doNothing().when(messagePublisher).publishReply(any(DocumentStorageReplyEvent.class));

        // When
        service.handleProcessCommand(command);

        // Then
        verify(messagePublisher).publishEvent(any(DocumentStoredEvent.class));
        verify(messagePublisher).publishReply(any(DocumentStorageReplyEvent.class));
    }

    @Test
    void handleProcessDocumentCommand_alreadyExists_skips() {
        // Given
        ProcessDocumentStorageCommand command = new ProcessDocumentStorageCommand(
            "saga-123", "doc-123", "http://minio:9000/doc.pdf", 1024
        );

        when(storageService.existsByInvoiceAndType("doc-123", DocumentType.INVOICE_PDF))
            .thenReturn(true);

        // When
        service.handleProcessCommand(command);

        // Then
        verify(pdfDownloadService, never()).downloadPdf(anyString());
        verify(storageService, never()).store(any(), any(), any(), any());
    }

    @Test
    void handleProcessDocumentCommand_downloadFails_publishesFailure() {
        // Given
        ProcessDocumentStorageCommand command = new ProcessDocumentStorageCommand(
            "saga-123", "doc-123", "http://minio:9000/doc.pdf", 1024
        );

        when(storageService.existsByInvoiceAndType("doc-123", DocumentType.INVOICE_PDF))
            .thenReturn(false);
        when(pdfDownloadService.downloadPdf(command.documentUrl()))
            .thenThrow(new StorageFailedException("Connection timeout"));

        // When
        service.handleProcessCommand(command);

        // Then
        verify(messagePublisher).publishReply(argThat(reply ->
            reply.sagaId().equals("saga-123") && reply.status() == SagaReplyStatus.FAILED
        ));
    }
```

**Step 2: Implement the handler**

Update `handleProcessCommand(ProcessDocumentStorageCommand command)` in `SagaOrchestrationService.java`:

```java
    @Override
    @Transactional
    public void handleProcessCommand(ProcessDocumentStorageCommand command) {
        log.info("Handling ProcessDocumentStorageCommand for saga: {}, document: {}",
                 command.sagaId(), command.documentId());

        try {
            // 1. Idempotency check
            if (storageService.existsByInvoiceAndType(command.documentId(), DocumentType.INVOICE_PDF)) {
                log.info("Document already exists for invoice: {}, type: INVOICE_PDF",
                         command.documentId());
                publishAlreadyExistsReply(command);
                return;
            }

            // 2. Download PDF from orchestrator-provided URL
            byte[] content = pdfDownloadService.downloadPdf(command.documentUrl());

            // 3. Store document
            String filename = command.documentId() + ".pdf";
            StoredDocument stored = storageService.store(
                content,
                filename,
                DocumentType.INVOICE_PDF,
                command.documentId()
            );

            // 4. Publish event and reply via outbox
            DocumentStoredEvent event = new DocumentStoredEvent(
                stored.id(),
                stored.invoiceId(),
                stored.documentType()
            );
            messagePublisher.publishEvent(event);

            DocumentStorageReplyEvent reply = DocumentStorageReplyEvent.success(
                command.sagaId(),
                stored.id()
            );
            messagePublisher.publishReply(reply);

            log.info("Successfully processed ProcessDocumentStorageCommand for saga: {}",
                     command.sagaId());

        } catch (StorageFailedException | InvalidDocumentException e) {
            log.error("Failed to process document storage command for saga: {}",
                      command.sagaId(), e);
            publishFailureReply(command, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing document storage command for saga: {}",
                      command.sagaId(), e);
            publishFailureReply(command, "Unexpected error: " + e.getMessage());
        }
    }

    private void publishAlreadyExistsReply(ProcessDocumentStorageCommand command) {
        DocumentStorageReplyEvent reply = DocumentStorageReplyEvent.success(
            command.sagaId(),
            command.documentId() // Use documentId as the stored ID
        );
        messagePublisher.publishReply(reply);
    }

    private void publishFailureReply(ProcessDocumentStorageCommand command, String error) {
        DocumentStorageReplyEvent reply = DocumentStorageReplyEvent.failure(
            command.sagaId(),
            error
        );
        messagePublisher.publishReply(reply);
    }
```

**Step 3: Run tests**

Run: `mvn test -Dtest=SagaOrchestrationServiceTest -q`
Expected: New tests PASS

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/service/SagaOrchestrationService.java
git add src/test/java/com/wpanther/storage/domain/service/SagaOrchestrationServiceTest.java
git commit -m "feat: implement ProcessDocumentStorageCommand handler"
```

---

### Task 19: Implement ProcessSignedXmlStorageCommand Handler

**Files:**
- Modify: `src/main/java/com/wpanther/storage/domain/service/SagaOrchestrationService.java`

**Step 1: Add to imports and implement handler**

Update the handler in `SagaOrchestrationService.java`:

```java
    @Override
    @Transactional
    public void handleProcessCommand(ProcessSignedXmlStorageCommand command) {
        log.info("Handling ProcessSignedXmlStorageCommand for saga: {}, document: {}",
                 command.sagaId(), command.documentId());

        try {
            // 1. Idempotency check
            if (storageService.existsByInvoiceAndType(command.documentId(), DocumentType.SIGNED_XML)) {
                log.info("Signed XML already exists for invoice: {}", command.documentId());
                publishAlreadyExistsXmlReply(command);
                return;
            }

            // 2. Store signed XML content
            byte[] content = command.signedXmlContent().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String filename = command.documentId() + ".xml";

            StoredDocument stored = storageService.store(
                content,
                filename,
                DocumentType.SIGNED_XML,
                command.documentId()
            );

            // 3. Publish reply via outbox
            SignedXmlStorageReplyEvent reply = SignedXmlStorageReplyEvent.success(
                command.sagaId(),
                stored.id()
            );
            messagePublisher.publishReply(reply);

            log.info("Successfully stored signed XML for saga: {}", command.sagaId());

        } catch (StorageFailedException | InvalidDocumentException e) {
            log.error("Failed to store signed XML for saga: {}", command.sagaId(), e);
            publishFailureXmlReply(command, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error storing signed XML for saga: {}", command.sagaId(), e);
            publishFailureXmlReply(command, "Unexpected error: " + e.getMessage());
        }
    }

    private void publishAlreadyExistsXmlReply(ProcessSignedXmlStorageCommand command) {
        SignedXmlStorageReplyEvent reply = SignedXmlStorageReplyEvent.success(
            command.sagaId(),
            command.documentId()
        );
        messagePublisher.publishReply(reply);
    }

    private void publishFailureXmlReply(ProcessSignedXmlStorageCommand command, String error) {
        SignedXmlStorageReplyEvent reply = SignedXmlStorageReplyEvent.failure(
            command.sagaId(),
            error
        );
        messagePublisher.publishReply(reply);
    }
```

**Step 2: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/service/SagaOrchestrationService.java
git commit -m "feat: implement ProcessSignedXmlStorageCommand handler"
```

---

### Task 20: Implement ProcessPdfStorageCommand Handler

**Files:**
- Modify: `src/main/java/com/wpanther/storage/domain/service/SagaOrchestrationService.java`

**Step 1: Implement handler**

Update the handler in `SagaOrchestrationService.java`:

```java
    @Override
    @Transactional
    public void handleProcessCommand(ProcessPdfStorageCommand command) {
        log.info("Handling ProcessPdfStorageCommand for saga: {}, pdfUrl: {}",
                 command.sagaId(), command.pdfUrl());

        try {
            // 1. Idempotency check - UNSIGNED_PDF type for this step
            if (storageService.existsByInvoiceAndType(command.documentId(), DocumentType.UNSIGNED_PDF)) {
                log.info("Unsigned PDF already exists for invoice: {}", command.documentId());
                publishAlreadyExistsPdfReply(command);
                return;
            }

            // 2. Download unsigned PDF from MinIO
            byte[] content = pdfDownloadService.downloadPdf(command.pdfUrl());

            // 3. Verify size matches expected
            if (content.length != command.pdfSize()) {
                log.warn("Downloaded PDF size {} does not match expected {}",
                         content.length, command.pdfSize());
            }

            // 4. Store as UNSIGNED_PDF
            String filename = command.documentId() + "_unsigned.pdf";

            StoredDocument stored = storageService.store(
                content,
                filename,
                DocumentType.UNSIGNED_PDF,
                command.documentId()
            );

            // 5. Publish reply with storedDocumentUrl for SIGN_PDF step
            PdfStorageReplyEvent reply = PdfStorageReplyEvent.success(
                command.sagaId(),
                stored.id(),
                "/api/v1/documents/" + stored.id() + "/download" // storedDocumentUrl
            );
            messagePublisher.publishReply(reply);

            log.info("Successfully stored unsigned PDF for saga: {}, storedUrl: {}",
                     command.sagaId(), reply.storedDocumentUrl());

        } catch (StorageFailedException | InvalidDocumentException e) {
            log.error("Failed to store unsigned PDF for saga: {}", command.sagaId(), e);
            publishFailurePdfReply(command, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error storing unsigned PDF for saga: {}", command.sagaId(), e);
            publishFailurePdfReply(command, "Unexpected error: " + e.getMessage());
        }
    }

    private void publishAlreadyExistsPdfReply(ProcessPdfStorageCommand command) {
        // For existing, find the document and return its URL
        storageService.findByInvoice(command.documentId())
            .stream()
            .filter(doc -> doc.documentType() == DocumentType.UNSIGNED_PDF)
            .findFirst()
            .ifPresent(doc -> {
                PdfStorageReplyEvent reply = PdfStorageReplyEvent.success(
                    command.sagaId(),
                    doc.id(),
                    "/api/v1/documents/" + doc.id() + "/download"
                );
                messagePublisher.publishReply(reply);
            });
    }

    private void publishFailurePdfReply(ProcessPdfStorageCommand command, String error) {
        PdfStorageReplyEvent reply = PdfStorageReplyEvent.failure(
            command.sagaId(),
            error
        );
        messagePublisher.publishReply(reply);
    }
```

**Step 2: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/service/SagaOrchestrationService.java
git commit -m "feat: implement ProcessPdfStorageCommand handler"
```

---

### Task 21: Implement DocumentStorage Compensation Handler

**Files:**
- Modify: `src/main/java/com/wpanther/storage/domain/service/SagaOrchestrationService.java`

**Step 1: Implement handler**

Update the handler in `SagaOrchestrationService.java`:

```java
    @Override
    @Transactional
    public void handleCompensation(CompensateDocumentStorageCommand command) {
        log.info("Handling CompensateDocumentStorageCommand for saga: {}, document: {}",
                 command.sagaId(), command.documentId());

        // Idempotent deletion - delete all documents for the invoice
        java.util.List<StoredDocument> documents = storageService.findByInvoice(command.documentId());

        for (StoredDocument doc : documents) {
            try {
                storageService.delete(doc.id());
                log.info("Compensated document storage: {} for saga: {}", doc.id(), command.sagaId());
            } catch (Exception e) {
                log.warn("Failed to delete document: {} during compensation for saga: {}",
                         doc.id(), command.sagaId(), e);
            }
        }

        log.info("Completed compensation for saga: {}", command.sagaId());
    }
```

**Step 2: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/service/SagaOrchestrationService.java
git commit -m "feat: implement DocumentStorage compensation handler"
```

---

### Task 22: Implement SignedXmlStorage Compensation Handler

**Files:**
- Modify: `src/main/java/com/wpanther/storage/domain/service/SagaOrchestrationService.java`

**Step 1: Implement handler**

Update the handler in `SagaOrchestrationService.java`:

```java
    @Override
    @Transactional
    public void handleCompensation(CompensateSignedXmlStorageCommand command) {
        log.info("Handling CompensateSignedXmlStorageCommand for saga: {}, document: {}",
                 command.sagaId(), command.documentId());

        // Delete signed XML document
        storageService.findByInvoice(command.documentId())
            .stream()
            .filter(doc -> doc.documentType() == DocumentType.SIGNED_XML)
            .findFirst()
            .ifPresent(doc -> {
                storageService.delete(doc.id());
                log.info("Compensated signed XML storage: {} for saga: {}", doc.id(), command.sagaId());
            });

        log.info("Completed signed XML compensation for saga: {}", command.sagaId());
    }
```

**Step 2: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/service/SagaOrchestrationService.java
git commit -m "feat: implement SignedXmlStorage compensation handler"
```

---

### Task 23: Implement PdfStorage Compensation Handler

**Files:**
- Modify: `src/main/java/com/wpanther/storage/domain/service/SagaOrchestrationService.java`

**Step 1: Implement handler**

Update the handler in `SagaOrchestrationService.java`:

```java
    @Override
    @Transactional
    public void handleCompensation(CompensatePdfStorageCommand command) {
        log.info("Handling CompensatePdfStorageCommand for saga: {}, document: {}",
                 command.sagaId(), command.documentId());

        // Delete UNSIGNED_PDF document
        storageService.findByInvoice(command.documentId())
            .stream()
            .filter(doc -> doc.documentType() == DocumentType.UNSIGNED_PDF)
            .findFirst()
            .ifPresent(doc -> {
                storageService.delete(doc.id());
                log.info("Compensated unsigned PDF storage: {} for saga: {}", doc.id(), command.sagaId());
            });

        log.info("Completed PDF storage compensation for saga: {}", command.sagaId());
    }
```

**Step 2: Remove @Override annotations and UnsupportedOperationException**

Remove the `@Override` annotations and `throw new UnsupportedOperationException` from all handlers that are now implemented.

**Step 3: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/service/SagaOrchestrationService.java
git commit -m "feat: implement PdfStorage compensation handler"
```

---

## Phase 4: Reorganize Infrastructure Adapters

### Task 24: Create Adapter Directory Structure

**Files:**
- Create: `src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/rest/` (directory)
- Create: `src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/messaging/` (directory)
- Create: `src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/security/` (directory)
- Create: `src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/storage/` (directory)
- Create: `src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/persistence/` (directory)
- Create: `src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/messaging/` (directory)

**Step 1: Create directories**

Run: `mkdir -p src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/rest`
Run: `mkdir -p src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/messaging`
Run: `mkdir -p src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/security`
Run: `mkdir -p src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/storage`
Run: `mkdir -p src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/persistence`
Run: `mkdir -p src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/messaging`

**Step 2: Verify**

Run: `ls -R src/main/java/com/wpanther/storage/infrastructure/adapter/`
Expected: List of all adapter directories

**Step 3: Commit**

```bash
git add src/main/java/com/wpanther/storage/infrastructure/adapter/
git commit -m "refactor: create adapter directory structure for inbound/outbound"
```

---

### Task 25: Move and Refactor LocalFileStorageProvider to LocalFileStorageAdapter

**Files:**
- Read: `src/main/java/com/wpanther/storage/infrastructure/storage/LocalFileStorageProvider.java`
- Create: `src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/storage/LocalFileStorageAdapter.java`
- Test: `src/test/java/com/wpanther/storage/infrastructure/adapter/outbound/storage/LocalFileStorageAdapterTest.java`

**Step 1: Read existing file**

Run: `cat src/main/java/com/wpanther/storage/infrastructure/storage/LocalFileStorageProvider.java`
Expected: See existing implementation

**Step 2: Create new adapter file**

Create `src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/storage/LocalFileStorageAdapter.java`:

```java
package com.wpanther.storage.infrastructure.adapter.outbound.storage;

import com.wpanther.storage.domain.port.outbound.StorageProviderPort;
import com.wpanther.storage.domain.model.StorageResult;
import com.wpanther.storage.domain.model.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

/**
 * Local filesystem storage adapter.
 * Implements StorageProviderPort for local file storage.
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local")
public class LocalFileStorageAdapter implements StorageProviderPort {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageAdapter.class);

    private final Path basePath;

    public LocalFileStorageAdapter(@Value("${app.storage.local.path}") String path) {
        this.basePath = Path.of(path);
        ensureBaseDirectoryExists();
    }

    @Override
    public StorageResult store(String documentId, InputStream content,
                               String originalFilename, long size) {
        LocalDate now = LocalDate.now();
        String extension = getFileExtension(originalFilename);
        Path relativePath = Path.of(
            String.valueOf(now.getYear()),
            String.format("%02d", now.getMonthValue()),
            String.format("%02d", now.getDayOfMonth()),
            documentId + "." + extension
        );
        Path fullPath = basePath.resolve(relativePath);

        try {
            Files.createDirectories(fullPath.getParent());
            long bytesWritten = Files.copy(content, fullPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("Stored document: {} at: {}, size: {} bytes",
                     documentId, relativePath, bytesWritten);

            return StorageResult.success(relativePath.toString(), "local");

        } catch (IOException e) {
            throw new StorageException("Failed to store document: " + documentId, e);
        }
    }

    @Override
    public InputStream retrieve(String storageLocation) {
        Path fullPath = basePath.resolve(storageLocation);

        if (!Files.exists(fullPath)) {
            throw new StorageException("File not found: " + storageLocation);
        }

        try {
            return Files.newInputStream(fullPath);
        } catch (IOException e) {
            throw new StorageException("Failed to retrieve file: " + storageLocation, e);
        }
    }

    @Override
    public void delete(String storageLocation) {
        Path fullPath = basePath.resolve(storageLocation);

        try {
            Files.deleteIfExists(fullPath);
            log.info("Deleted file at: {}", storageLocation);
        } catch (IOException e) {
            throw new StorageException("Failed to delete file: " + storageLocation, e);
        }
    }

    @Override
    public boolean exists(String storageLocation) {
        Path fullPath = basePath.resolve(storageLocation);
        return Files.exists(fullPath);
    }

    private void ensureBaseDirectoryExists() {
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new StorageException("Failed to create base directory: " + basePath, e);
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "bin";
        }
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "bin";
    }
}
```

**Step 3: Update test file**

Move and update `src/test/java/com/wpanther/storage/infrastructure/storage/LocalFileStorageProviderTest.java` to `src/test/java/com/wpanther/storage/infrastructure/adapter/outbound/storage/LocalFileStorageAdapterTest.java`:

```java
package com.wpanther.storage.infrastructure.adapter.outbound.storage;

import com.wpanther.storage.domain.port.outbound.StorageProviderPort;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class LocalFileStorageAdapterTest {

    @Test
    void storeAndRetrieve_success(@TempDir Path tempDir) throws IOException {
        // Given
        StorageProviderPort adapter = new LocalFileStorageAdapter(tempDir.toString());
        byte[] content = "test content".getBytes();

        // When
        var result = adapter.store("doc-123",
            new ByteArrayInputStream(content), "test.pdf", content.length);

        // Then
        assertThat(result.location()).isNotEmpty();
        assertThat(result.provider()).isEqualTo("local");

        // And
        byte[] retrieved = adapter.retrieve(result.location()).readAllBytes();
        assertThat(retrieved).isEqualTo(content);
    }

    @Test
    void delete_removesFile(@TempDir Path tempDir) throws IOException {
        // Given
        StorageProviderPort adapter = new LocalFileStorageAdapter(tempDir.toString());
        byte[] content = "test content".getBytes();
        var result = adapter.store("doc-456",
            new ByteArrayInputStream(content), "test.pdf", content.length);

        // When
        adapter.delete(result.location());

        // Then
        assertThat(adapter.exists(result.location())).isFalse();
    }
}
```

**Step 4: Run test**

Run: `mvn test -Dtest=LocalFileStorageAdapterTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/storage/LocalFileStorageAdapter.java
git add src/test/java/com/wpanther/storage/infrastructure/adapter/outbound/storage/LocalFileStorageAdapterTest.java
git commit -m "refactor: move LocalFileStorageProvider to LocalFileStorageAdapter"
```

---

### Task 26: Move and Refactor S3FileStorageProvider to S3FileStorageAdapter

**Files:**
- Read: `src/main/java/com/wpanther/storage/infrastructure/storage/S3FileStorageProvider.java`
- Create: `src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/storage/S3FileStorageAdapter.java`
- Test: `src/test/java/com/wpanther/storage/infrastructure/adapter/outbound/storage/S3FileStorageAdapterTest.java`

**Step 1: Read existing file**

Run: `cat src/main/java/com/wpanther/storage/infrastructure/storage/S3FileStorageProvider.java`
Expected: See existing implementation

**Step 2: Create new adapter file**

Create `src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/storage/S3FileStorageAdapter.java`:

```java
package com.wpanther.storage.infrastructure.adapter.outbound.storage;

import com.wpanther.storage.domain.port.outbound.StorageProviderPort;
import com.wpanther.storage.domain.model.StorageResult;
import com.wpanther.storage.domain.model.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;

/**
 * S3/MinIO storage adapter.
 * Implements StorageProviderPort for S3-compatible storage.
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
public class S3FileStorageAdapter implements StorageProviderPort {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorageAdapter.class);

    private final S3Client s3Client;
    private final String bucketName;

    public S3FileStorageAdapter(S3Client s3Client,
                                @Value("${app.storage.s3.bucket-name}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        ensureBucketExists();
    }

    @Override
    public StorageResult store(String documentId, InputStream content,
                               String originalFilename, long size) {
        LocalDate now = LocalDate.now();
        String extension = getFileExtension(originalFilename);
        String key = String.format("%d/%02d/%02d/%s_%s.%s",
            now.getYear(),
            now.getMonthValue(),
            now.getDayOfMonth(),
            documentId,
            sanitizeFilename(originalFilename),
            extension
        );

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentLength(size)
                .build();

            s3Client.putObject(request, RequestBody.fromInputStream(content, size));

            log.info("Stored document: {} in S3 at: {}, bucket: {}",
                     documentId, key, bucketName);

            return StorageResult.success(key, "s3");

        } catch (Exception e) {
            throw new StorageException("Failed to store document in S3: " + documentId, e);
        }
    }

    @Override
    public InputStream retrieve(String storageLocation) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(storageLocation)
                .build();

            return s3Client.getObject(request);

        } catch (NoSuchKeyException e) {
            throw new StorageException("File not found in S3: " + storageLocation, e);
        } catch (Exception e) {
            throw new StorageException("Failed to retrieve file from S3: " + storageLocation, e);
        }
    }

    @Override
    public void delete(String storageLocation) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(storageLocation)
                .build();

            s3Client.deleteObject(request);
            log.info("Deleted file from S3: {}", storageLocation);

        } catch (Exception e) {
            throw new StorageException("Failed to delete file from S3: " + storageLocation, e);
        }
    }

    @Override
    public boolean exists(String storageLocation) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(storageLocation)
                .build();

            s3Client.headObject(request);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureBucketExists() {
        try {
            HeadBucketRequest request = HeadBucketRequest.builder()
                .bucket(bucketName)
                .build();
            s3Client.headBucket(request);
        } catch (NoSuchBucketException e) {
            try {
                CreateBucketRequest createRequest = CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
                s3Client.createBucket(createRequest);
                log.info("Created S3 bucket: {}", bucketName);
            } catch (Exception ex) {
                throw new StorageException("Failed to create bucket: " + bucketName, ex);
            }
        } catch (Exception e) {
            log.warn("Could not verify bucket existence: {}", bucketName, e);
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "bin";
        }
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "bin";
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "file";
        }
        // Remove extension and sanitize
        int lastDot = filename.lastIndexOf('.');
        String name = lastDot > 0 ? filename.substring(0, lastDot) : filename;
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
```

**Step 3: Update test file**

Move and update test file similarly to Task 25.

**Step 4: Run test**

Run: `mvn test -Dtest=S3FileStorageAdapterTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/storage/S3FileStorageAdapter.java
git add src/test/java/com/wpanther/storage/infrastructure/adapter/outbound/storage/S3FileStorageAdapterTest.java
git commit -m "refactor: move S3FileStorageProvider to S3FileStorageAdapter"
```

---

### Task 27: Move MongoDocumentRepository to MongoDocumentAdapter

**Files:**
- Read: `src/main/java/com/wpanther/storage/infrastructure/persistence/MongoDocumentRepository.java`
- Create: `src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/persistence/MongoDocumentAdapter.java`

**Step 1: Read existing file**

Run: `cat src/main/java/com/wpanther/storage/infrastructure/persistence/MongoDocumentRepository.java`
Expected: See existing implementation

**Step 2: Create new adapter file**

Create `src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/persistence/MongoDocumentAdapter.java`:

```java
package com.wpanther.storage.infrastructure.adapter.outbound.persistence;

import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.port.outbound.DocumentRepositoryPort;
import com.wpanther.storage.infrastructure.persistence.StoredDocumentEntity;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB adapter for document metadata persistence.
 * Implements DocumentRepositoryPort.
 */
@Component
public class MongoDocumentAdapter implements DocumentRepositoryPort {

    private final MongoTemplate mongoTemplate;

    public MongoDocumentAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public StoredDocument save(StoredDocument document) {
        StoredDocumentEntity entity = toEntity(document);
        StoredDocumentEntity saved = mongoTemplate.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<StoredDocument> findById(String id) {
        StoredDocumentEntity entity = mongoTemplate.findById(id, StoredDocumentEntity.class);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public List<StoredDocument> findByInvoiceId(String invoiceId) {
        Query query = new Query(Criteria.where("invoiceId").is(invoiceId));
        List<StoredDocumentEntity> entities = mongoTemplate.find(query, StoredDocumentEntity.class);
        return entities.stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Optional<StoredDocument> findByInvoiceIdAndDocumentType(String invoiceId, DocumentType type) {
        Query query = new Query.Builder()
            .addCriteria(Criteria.where("invoiceId").is(invoiceId))
            .addCriteria(Criteria.where("documentType").is(type))
            .build();
        StoredDocumentEntity entity = mongoTemplate.findOne(query, StoredDocumentEntity.class);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public void deleteById(String id) {
        mongoTemplate.remove(id);
    }

    @Override
    public boolean existsByInvoiceIdAndDocumentType(String invoiceId, DocumentType type) {
        Query query = new Query.Builder()
            .addCriteria(Criteria.where("invoiceId").is(invoiceId))
            .addCriteria(Criteria.where("documentType").is(type))
            .build();
        return mongoTemplate.exists(query, StoredDocumentEntity.class);
    }

    private StoredDocumentEntity toEntity(StoredDocument domain) {
        return StoredDocumentEntity.builder()
            .id(domain.id())
            .invoiceId(domain.invoiceId())
            .documentType(domain.documentType())
            .originalFilename(domain.originalFilename())
            .storageLocation(domain.storageLocation())
            .storageProvider(domain.storageProvider())
            .fileSize(domain.fileSize())
            .checksum(domain.checksum())
            .createdAt(domain.createdAt())
            .build();
    }

    private StoredDocument toDomain(StoredDocumentEntity entity) {
        return StoredDocument.builder()
            .id(entity.getId())
            .invoiceId(entity.getInvoiceId())
            .documentType(entity.getDocumentType())
            .originalFilename(entity.getOriginalFilename())
            .storageLocation(entity.getStorageLocation())
            .storageProvider(entity.getStorageProvider())
            .fileSize(entity.getFileSize())
            .checksum(entity.getChecksum())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}
```

**Step 3: Commit**

```bash
git add src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/persistence/MongoDocumentAdapter.java
git commit -m "refactor: move MongoDocumentRepository to MongoDocumentAdapter"
```

---

### Task 28: Move Outbox Repository Implementations

**Files:**
- Move: `src/main/java/com/wpanther/storage/infrastructure/persistence/outbox/` → `src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/persistence/outbox/`

**Step 1: Move outbox directory**

Run: `mkdir -p src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/persistence/outbox`
Run: `mv src/main/java/com/wpanther/storage/infrastructure/persistence/outbox/* src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/persistence/outbox/`

**Step 2: Update package declarations in moved files**

For each file in `outbox/`, update package from:
```java
package com.wpanther.storage.infrastructure.persistence.outbox;
```
to:
```java
package com.wpanther.storage.infrastructure.adapter.outbound.persistence.outbox;
```

**Step 3: Commit**

```bash
git add src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/persistence/outbox/
git commit -m "refactor: move outbox implementations to adapter structure"
```

---

### Task 29: Create MessagePublisherAdapter

**Files:**
- Create: `src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/messaging/MessagePublisherAdapter.java`
- Create: `src/test/java/com/wpanther/storage/infrastructure/adapter/outbound/messaging/MessagePublisherAdapterTest.java`

**Step 1: Write test**

Create `src/test/java/com/wpanther/storage/infrastructure/adapter/outbound/messaging/MessagePublisherAdapterTest.java`:

```java
package com.wpanther.storage.infrastructure.adapter.outbound.messaging;

import com.wpanther.storage.domain.event.*;
import com.wpanther.storage.domain.port.outbound.MessagePublisherPort;
import com.wpanther.saga.commons.outbox.OutboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessagePublisherAdapterTest {

    @Mock private OutboxService outboxService;

    @InjectMocks private MessagePublisherAdapter adapter;

    @Test
    void publishEvent_savesToOutbox() {
        // Given
        DocumentStoredEvent event = new DocumentStoredEvent("doc-123", "INV-001", DocumentType.INVOICE_PDF);

        // When
        adapter.publishEvent(event);

        // Then
        verify(outboxService).save(any());
    }
}
```

**Step 2: Write adapter**

Create `src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/messaging/MessagePublisherAdapter.java`:

```java
package com.wpanther.storage.infrastructure.adapter.outbound.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.commons.outbox.OutboxEvent;
import com.wpanther.saga.commons.outbox.OutboxService;
import com.wpanther.storage.domain.event.*;
import com.wpanther.storage.domain.port.outbound.MessagePublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Message publisher adapter using the outbox pattern.
 * Implements MessagePublisherPort.
 */
@Component
public class MessagePublisherAdapter implements MessagePublisherPort {

    private static final Logger log = LoggerFactory.getLogger(MessagePublisherAdapter.class);

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public MessagePublisherAdapter(OutboxService outboxService, ObjectMapper objectMapper) {
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishEvent(DocumentStoredEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outbox = OutboxEvent.builder()
                .aggregateId(event.documentId())
                .aggregateType("StoredDocument")
                .eventType("DocumentStoredEvent")
                .payload(payload)
                .topic("document.stored")
                .build();

            outboxService.save(outbox);
            log.debug("Published DocumentStoredEvent for document: {}", event.documentId());

        } catch (Exception e) {
            log.error("Failed to publish DocumentStoredEvent", e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }

    @Override
    public void publishReply(DocumentStorageReplyEvent reply) {
        try {
            String payload = objectMapper.writeValueAsString(reply);
            OutboxEvent outbox = OutboxEvent.builder()
                .aggregateId(reply.sagaId())
                .aggregateType("DocumentStorageSaga")
                .eventType("DocumentStorageReplyEvent")
                .payload(payload)
                .topic("saga.reply.document-storage")
                .build();

            outboxService.save(outbox);
            log.debug("Published DocumentStorageReplyEvent for saga: {}", reply.sagaId());

        } catch (Exception e) {
            log.error("Failed to publish DocumentStorageReplyEvent", e);
            throw new RuntimeException("Failed to publish reply", e);
        }
    }

    @Override
    public void publishReply(SignedXmlStorageReplyEvent reply) {
        try {
            String payload = objectMapper.writeValueAsString(reply);
            OutboxEvent outbox = OutboxEvent.builder()
                .aggregateId(reply.sagaId())
                .aggregateType("SignedXmlStorageSaga")
                .eventType("SignedXmlStorageReplyEvent")
                .payload(payload)
                .topic("saga.reply.signedxml-storage")
                .build();

            outboxService.save(outbox);
            log.debug("Published SignedXmlStorageReplyEvent for saga: {}", reply.sagaId());

        } catch (Exception e) {
            log.error("Failed to publish SignedXmlStorageReplyEvent", e);
            throw new RuntimeException("Failed to publish reply", e);
        }
    }

    @Override
    public void publishReply(PdfStorageReplyEvent reply) {
        try {
            String payload = objectMapper.writeValueAsString(reply);
            OutboxEvent outbox = OutboxEvent.builder()
                .aggregateId(reply.sagaId())
                .aggregateType("PdfStorageSaga")
                .eventType("PdfStorageReplyEvent")
                .payload(payload)
                .topic("saga.reply.pdf-storage")
                .build();

            outboxService.save(outbox);
            log.debug("Published PdfStorageReplyEvent for saga: {}", reply.sagaId());

        } catch (Exception e) {
            log.error("Failed to publish PdfStorageReplyEvent", e);
            throw new RuntimeException("Failed to publish reply", e);
        }
    }
}
```

**Step 3: Run test**

Run: `mvn test -Dtest=MessagePublisherAdapterTest -q`
Expected: PASS

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/messaging/MessagePublisherAdapter.java
git add src/test/java/com/wpanther/storage/infrastructure/adapter/outbound/messaging/MessagePublisherAdapterTest.java
git commit -m "feat: implement MessagePublisherAdapter"
```

---

## Phase 5: Create Inbound Adapters

### Task 30: Move and Update DocumentStorageController

**Files:**
- Read: `src/main/java/com/wpanther/storage/application/controller/DocumentStorageController.java`
- Create: `src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/rest/DocumentStorageController.java`

**Step 1: Read existing file**

Run: `cat src/main/java/com/wpanther/storage/application/controller/DocumentStorageController.java`
Expected: See existing implementation

**Step 2: Create new controller**

Create `src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/rest/DocumentStorageController.java`:

```java
package com.wpanther.storage.infrastructure.adapter.inbound.rest;

import com.wpanther.storage.domain.port.inbound.DocumentStorageUseCase;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.service.FileStorageDomainService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * REST adapter for document storage operations.
 * Implements HTTP endpoints and delegates to DocumentStorageUseCase.
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentStorageController {

    private final DocumentStorageUseCase documentStorageUseCase;
    private final FileStorageDomainService fileStorageDomainService;

    public DocumentStorageController(DocumentStorageUseCase documentStorageUseCase,
                                     FileStorageDomainService fileStorageDomainService) {
        this.documentStorageUseCase = documentStorageUseCase;
        this.fileStorageDomainService = fileStorageDomainService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoredDocument> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") DocumentType type,
            @RequestParam("invoiceId") String invoiceId) {

        try {
            byte[] content = file.getBytes();
            StoredDocument doc = documentStorageUseCase.storeDocument(
                content,
                file.getOriginalFilename(),
                type,
                invoiceId
            );

            return ResponseEntity.ok(doc);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<StoredDocument> getDocument(@PathVariable String id) {
        return documentStorageUseCase.getDocument(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/metadata")
    public ResponseEntity<StoredDocument> getMetadata(@PathVariable String id) {
        return documentStorageUseCase.getDocument(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable String id) {
        return documentStorageUseCase.getDocument(id)
            .map(doc -> {
                try (InputStream content = fileStorageDomainService.downloadContent(doc.storageLocation())) {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                    headers.setContentDispositionFormData("attachment", doc.originalFilename());

                    return ResponseEntity.ok()
                        .headers(headers)
                        .body(content.readAllBytes());

                } catch (IOException e) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/invoice/{invoiceId}")
    public ResponseEntity<List<StoredDocument>> getDocumentsByInvoice(@PathVariable String invoiceId) {
        List<StoredDocument> docs = documentStorageUseCase.getDocumentsByInvoice(invoiceId);
        return ResponseEntity.ok(docs);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) {
        documentStorageUseCase.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}
```

**Step 3: Update test file**

Move and update `src/test/java/com/wpanther/storage/application/controller/DocumentStorageControllerTest.java` to `src/test/java/com/wpanther/storage/infrastructure/adapter/inbound/rest/DocumentStorageControllerTest.java` and update imports.

**Step 4: Run test**

Run: `mvn test -Dtest=DocumentStorageControllerTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/rest/DocumentStorageController.java
git add src/test/java/com/wpanther/storage/infrastructure/adapter/inbound/rest/DocumentStorageControllerTest.java
git commit -m "refactor: move DocumentStorageController to adapter structure"
```

---

### Task 31: Move AuthenticationController

**Files:**
- Move: `src/main/java/com/wpanther/storage/application/controller/AuthenticationController.java` → `src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/rest/AuthenticationController.java`

**Step 1: Move and update file**

Run: `mv src/main/java/com/wpanther/storage/application/controller/AuthenticationController.java src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/rest/AuthenticationController.java`

**Step 2: Update package declaration**

Change package to:
```java
package com.wpanther.storage.infrastructure.adapter.inbound.rest;
```

**Step 3: Commit**

```bash
git add src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/rest/AuthenticationController.java
git commit -m "refactor: move AuthenticationController to adapter structure"
```

---

### Task 32: Create SagaCommandAdapter (Camel Routes)

**Files:**
- Create: `src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/messaging/SagaCommandAdapter.java`
- Create: `src/test/java/com/wpanther/storage/infrastructure/adapter/inbound/messaging/SagaCommandAdapterTest.java`

**Step 1: Write the adapter**

Create `src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/messaging/SagaCommandAdapter.java`:

```java
package com.wpanther.storage.infrastructure.adapter.inbound.messaging;

import com.wpanther.storage.domain.event.*;
import com.wpanther.storage.domain.port.inbound.SagaCommandUseCase;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

/**
 * Camel-based messaging adapter for saga commands.
 * Consumes from Kafka topics and delegates to SagaCommandUseCase.
 */
@Component
public class SagaCommandAdapter extends RouteBuilder {

    private final SagaCommandUseCase sagaCommandUseCase;

    public SagaCommandAdapter(SagaCommandUseCase sagaCommandUseCase) {
        this.sagaCommandUseCase = sagaCommandUseCase;
    }

    @Override
    public void configure() {

        // Document Storage Command Consumer
        from("kafka:saga.command.document-storage")
            .routeId("saga-document-storage-command")
            .unmarshal().json(JsonLibrary.Jackson, ProcessDocumentStorageCommand.class)
            .bean(sagaCommandUseCase, "handleProcessCommand")
            .log("Processed document storage command: ${body.sagaId}");

        // Document Storage Compensation Consumer
        from("kafka:saga.compensation.document-storage")
            .routeId("saga-document-storage-compensation")
            .unmarshal().json(JsonLibrary.Jackson, CompensateDocumentStorageCommand.class)
            .bean(sagaCommandUseCase, "handleCompensation")
            .log("Processed document storage compensation: ${body.sagaId}");

        // Signed XML Storage Command Consumer
        from("kafka:saga.command.signedxml-storage")
            .routeId("saga-signedxml-storage-command")
            .unmarshal().json(JsonLibrary.Jackson, ProcessSignedXmlStorageCommand.class)
            .bean(sagaCommandUseCase, "handleProcessCommand")
            .log("Processed signed XML storage command: ${body.sagaId}");

        // Signed XML Storage Compensation Consumer
        from("kafka:saga.compensation.signedxml-storage")
            .routeId("saga-signedxml-storage-compensation")
            .unmarshal().json(JsonLibrary.Jackson, CompensateSignedXmlStorageCommand.class)
            .bean(sagaCommandUseCase, "handleCompensation")
            .log("Processed signed XML storage compensation: ${body.sagaId}");

        // PDF Storage Command Consumer
        from("kafka:saga.command.pdf-storage")
            .routeId("saga-pdf-storage-command")
            .unmarshal().json(JsonLibrary.Jackson, ProcessPdfStorageCommand.class)
            .bean(sagaCommandUseCase, "handleProcessCommand")
            .log("Processed PDF storage command: ${body.sagaId}");

        // PDF Storage Compensation Consumer
        from("kafka:saga.compensation.pdf-storage")
            .routeId("saga-pdf-storage-compensation")
            .unmarshal().json(JsonLibrary.Jackson, CompensatePdfStorageCommand.class)
            .bean(sagaCommandUseCase, "handleCompensation")
            .log("Processed PDF storage compensation: ${body.sagaId}");

        // Error handling with DLQ
        errorHandler(defaultErrorHandler()
            .maximumRedeliveries(3)
            .redeliveryDelay(1000)
            .backOffMultiplier(2.0)
            .maximumRedeliveryDelay(10000)
            .logExhausted(true)
            .logExhaustedMessage(true)
            .to("log:ERROR")
            .to("kafka:document-storage.dlq"));
    }
}
```

**Step 2: Write test**

Create `src/test/java/com/wpanther/storage/infrastructure/adapter/inbound/messaging/SagaCommandAdapterTest.java`:

```java
package com.wpanther.storage.infrastructure.adapter.inbound.messaging;

import com.wpanther.storage.domain.port.inbound.SagaCommandUseCase;
import org.apache.camel.CamelContext;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.Mockito.verify;

@CamelSpringBootTest
@SpringBootTest
class SagaCommandAdapterTest {

    @Autowired
    private CamelContext camelContext;

    @MockBean
    private SagaCommandUseCase sagaCommandUseCase;

    @Test
    void camelContext_startsSuccessfully() {
        assertThat(camelContext).isNotNull();
    }
}
```

**Step 3: Run test**

Run: `mvn test -Dtest=SagaCommandAdapterTest -q`
Expected: PASS

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/messaging/SagaCommandAdapter.java
git add src/test/java/com/wpanther/storage/infrastructure/adapter/inbound/messaging/SagaCommandAdapterTest.java
git commit -m "feat: implement SagaCommandAdapter with Camel routes"
```

---

### Task 33: Move Security Components to Adapter Structure

**Files:**
- Move: `src/main/java/com/wpanther/storage/infrastructure/security/` → `src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/security/`

**Step 1: Move security directory**

Run: `mv src/main/java/com/wpanther/storage/infrastructure/security src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/`

**Step 2: Update package declarations in all security files**

For each file in `security/`, update package from:
```java
package com.wpanther.storage.infrastructure.security;
```
to:
```java
package com.wpanther.storage.infrastructure.adapter.inbound.security;
```

**Step 3: Update imports in affected files**

Update any files that import from the old security package.

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/security/
git commit -m "refactor: move security components to adapter structure"
```

---

### Task 34: Rename JwtAuthenticationFilter to JwtAuthenticationAdapter

**Files:**
- Rename: `src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/security/JwtAuthenticationFilter.java` → `JwtAuthenticationAdapter.java`

**Step 1: Rename file and class**

Run: `mv src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/security/JwtAuthenticationFilter.java src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/security/JwtAuthenticationAdapter.java`

Update class declaration:
```java
public class JwtAuthenticationAdapter extends OncePerRequestFilter {
```

**Step 2: Update Spring Security configuration to use the renamed class**

Update `SecurityConfig.java` to reference `JwtAuthenticationAdapter`.

**Step 3: Update test files**

Rename and update test files accordingly.

**Step 4: Commit**

```bash
git add src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/security/
git add src/test/java/com/wpanther/storage/infrastructure/adapter/inbound/security/
git commit -m "refactor: rename JwtAuthenticationFilter to JwtAuthenticationAdapter"
```

---

## Phase 6: Configuration and Cleanup

### Task 35: Remove Old Application Directory

**Files:**
- Remove: `src/main/java/com/wpanther/storage/application/` (entire directory)

**Step 1: Verify all code has been migrated**

Run: `ls -la src/main/java/com/wpanther/storage/application/`
Expected: Directory should only contain files that have been migrated

**Step 2: Remove old application directory**

Run: `rm -rf src/main/java/com/wpanther/storage/application/`

**Step 3: Remove old infrastructure directories**

Run: `rm -rf src/main/java/com/wpanther/storage/infrastructure/storage/`
Run: `rm -rf src/main/java/com/wpanther/storage/infrastructure/persistence/`
Run: `rm -rf src/main/java/com/wpanther/storage/infrastructure/messaging/`
Run: `rm -rf src/main/java/com/wpanther/storage/infrastructure/config/SagaRouteConfig.java` (replaced by SagaCommandAdapter)

**Step 4: Update test directories**

Remove old test directories:
Run: `rm -rf src/test/java/com/wpanther/storage/application/`
Run: `rm -rf src/test/java/com/wpanther/storage/infrastructure/storage/`

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: remove old application and infrastructure directories"
```

---

### Task 35: Update SagaRouteConfig to Remove Old Routes

**Files:**
- Modify: `src/main/java/com/wpanther/storage/infrastructure/config/SagaRouteConfig.java`

**Step 1: Delete SagaRouteConfig**

Since `SagaCommandAdapter` now handles all saga routes, delete the old `SagaRouteConfig.java`:

Run: `rm src/main/java/com/wpanther/storage/infrastructure/config/SagaRouteConfig.java`

**Step 2: Delete its test**

Run: `rm src/test/java/com/wpanther/storage/infrastructure/config/SagaRouteConfigTest.java`

**Step 3: Commit**

```bash
git add -A
git commit -m "refactor: remove SagaRouteConfig (replaced by SagaCommandAdapter)"
```

---

### Task 36: Remove Old Publisher Classes

**Files:**
- Remove: `src/main/java/com/wpanther/storage/infrastructure/messaging/EventPublisher.java`
- Remove: `src/main/java/com/wpanther/storage/infrastructure/messaging/SagaReplyPublisher.java`
- Remove: `src/main/java/com/wpanther/storage/infrastructure/messaging/SignedXmlStorageSagaReplyPublisher.java`
- Remove: `src/main/java/com/wpanther/storage/infrastructure/messaging/PdfStorageSagaReplyPublisher.java`

**Step 1: Remove old publisher files**

These are now consolidated in `MessagePublisherAdapter`.

**Step 2: Commit**

```bash
git add -A
git commit -m "refactor: remove old publisher classes (consolidated in MessagePublisherAdapter)"
```

---

## Phase 7: Final Updates and Tests

### Task 37: Update All Imports Across Codebase

**Files:**
- Modify: All Java files with old imports

**Step 1: Find and replace imports**

Run: `find src/main/java -name "*.java" -exec sed -i 's/com\.wpanther\.storage\.application\.service/com.wpanther.storage.domain.service/g' {} \;`
Run: `find src/main/java -name "*.java" -exec sed -i 's/com\.wpanther\.storage\.domain\.service\.FileStorageProvider/com.wpanther.storage.domain.port.outbound.StorageProviderPort/g' {} \;`

**Step 2: Find and replace test imports**

Run: `find src/test/java -name "*.java" -exec sed -i 's/com\.wpanther\.storage\.application\.service/com.wpanther.storage.domain.service/g' {} \;`

**Step 3: Commit**

```bash
git add -A
git commit -m "refactor: update imports across codebase"
```

---

### Task 38: Run Full Test Suite

**Files:**
- All test files

**Step 1: Run all tests**

Run: `mvn clean test`

**Step 2: Verify all tests pass**

Expected: All tests PASS

**Step 3: If tests fail, fix and commit**

For each failure:
1. Investigate the failure
2. Fix the issue
3. Run tests again
4. Commit the fix

---

### Task 39: Build and Verify Package

**Files:**
- pom.xml (verify)

**Step 1: Build package**

Run: `mvn clean package -DskipTests`

**Step 2: Verify build succeeds**

Expected: BUILD SUCCESS

---

### Task 40: Final Code Review

**Step 1: Review package structure**

Run: `tree src/main/java/com/wpanther/storage/ -L 3`

Verify structure matches:
```
com.wpanther.storage/
├── DocumentStorageServiceApplication.java
├── domain/
│   ├── event/
│   ├── exception/
│   ├── model/
│   ├── port/
│   │   ├── inbound/
│   │   └── outbound/
│   └── service/
└── infrastructure/
    └── adapter/
        ├── inbound/
        │   ├── messaging/
        │   ├── rest/
        │   └── security/
        └── outbound/
            ├── messaging/
            ├── persistence/
            └── storage/
```

**Step 2: Verify no application directory exists**

Run: `ls src/main/java/com/wpanther/storage/ | grep application`
Expected: No output (application directory removed)

**Step 3: Verify dependency direction**

All dependencies should point inward toward domain:
- Adapters depend on ports (domain)
- Services depend on ports (domain)
- Domain has no dependencies on infrastructure

**Step 4: Final commit**

```bash
git add -A
git commit -m "feat: complete hexagonal architecture migration

- All ports defined in domain/port/
- All adapters in infrastructure/adapter/
- Domain services implement inbound ports
- Infrastructure adapters implement outbound ports
- All dependencies point inward toward domain
- Application layer removed
- Tests updated to match new structure"
```

---

## Validation Checklist

After completing all tasks, verify:

- [ ] All domain services have 90%+ test coverage with mocked ports
- [ ] All adapters have integration tests
- [ ] No references to `application/` package remain
- [ ] All dependencies point inward toward domain
- [ ] REST API endpoints return identical responses
- [ ] Saga commands process end-to-end without errors
- [ ] JWT authentication works as before
- [ ] Both local and S3 storage providers work correctly
- [ ] Outbox events are published to Kafka
- [ ] All tests pass (unit + integration)

---

## Completion

The hexagonal architecture migration is complete. The service now follows the ports and adapters pattern with:

- **Inbound Ports**: DocumentStorageUseCase, SagaCommandUseCase, AuthenticationUseCase
- **Outbound Ports**: StorageProviderPort, DocumentRepositoryPort, OutboxRepositoryPort, MessagePublisherPort
- **Domain Services**: FileStorageDomainService, SagaOrchestrationService, PdfDownloadDomainService
- **Inbound Adapters**: REST controllers, Kafka/Camel consumers, JWT authentication
- **Outbound Adapters**: Storage providers, repositories, message publishers

**Next Steps:**
- Run the service locally and verify all functionality
- Deploy to test environment
- Monitor metrics and logs
- Update documentation

---

**Document Version:** 1.0
**Status:** Ready for Implementation
