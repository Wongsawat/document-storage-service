# Hexagonal Architecture Migration - Design Document

**Date:** 2026-03-04
**Service:** Document Storage Service
**Architecture Pattern:** Hexagonal (Ports and Adapters)
**Approach:** Big Bang - Full Migration

---

## 1. Overview

This document describes the migration of the Document Storage Service from the current DDD layered architecture to hexagonal architecture (also known as ports and adapters pattern). The migration aims to improve testability, enforce dependency inversion, create clear boundaries, and future-proof the service for easier infrastructure component swapping.

### 1.1 Motivation

The migration addresses four key concerns:

1. **Testability** - Domain logic becomes completely testable without infrastructure dependencies (mock-free unit tests)
2. **Dependency Inversion** - All dependencies point inward toward the domain, following SOLID principles strictly
3. **Clear Boundaries** - Explicit ports (interfaces) and adapters (implementations) make the architecture more visible
4. **Future-Proofing** - Easier swapping of infrastructure components (databases, messaging, storage providers)

---

## 2. Package Structure

The new structure organizes code into three main layers with clear port/adapter boundaries:

```
com.wpanther.storage/
├── DocumentStorageServiceApplication.java    # Bootstrap only
│
├── domain/                                   # Core business logic (NO external dependencies)
│   ├── model/                                # Domain models, value objects, enums
│   │   ├── StoredDocument.java
│   │   ├── DocumentType.java
│   │   └── StorageResult.java                # Value object for storage results
│   ├── port/                                 # Port interfaces (contracts)
│   │   ├── inbound/                          # Driving ports (primary)
│   │   │   ├── DocumentStorageUseCase.java   # REST API operations
│   │   │   ├── SagaCommandUseCase.java       # Kafka saga operations
│   │   │   └── AuthenticationUseCase.java    # Auth operations
│   │   └── outbound/                         # Driven ports (secondary)
│   │       ├── StorageProviderPort.java      # File storage abstraction
│   │       ├── DocumentRepositoryPort.java   # MongoDB metadata
│   │       ├── OutboxRepositoryPort.java     # PostgreSQL outbox
│   │       └── MessagePublisherPort.java     # Kafka publishing
│   ├── service/                              # Domain service implementations
│   │   ├── FileStorageDomainService.java     # Core storage logic
│   │   ├── SagaOrchestrationService.java     # Saga coordination
│   │   └── PdfDownloadDomainService.java     # PDF download logic
│   └── exception/                            # Domain exceptions
│       ├── DocumentNotFoundException.java
│       ├── StorageFailedException.java
│       └── InvalidDocumentException.java
│
├── infrastructure/                           # External integrations
│   ├── adapter/                              # Adapter implementations
│   │   ├── inbound/                          # Driving adapters
│   │   │   ├── rest/                         # REST controllers
│   │   │   │   ├── DocumentStorageController.java
│   │   │   │   ├── AuthenticationController.java
│   │   │   │   └── ErrorResponseHandler.java
│   │   │   ├── messaging/                    # Kafka/Camel consumers
│   │   │   │   ├── SagaCommandAdapter.java   # Saga route handling
│   │   │   │   ├── SignedXmlStorageAdapter.java
│   │   │   │   └── PdfStorageAdapter.java
│   │   │   └── security/                     # JWT authentication
│   │   │       ├── JwtAuthenticationAdapter.java
│   │   │       ├── JwtService.java           # JWT token utilities
│   │   │       └── SecurityConfig.java
│   │   └── outbound/                         # Driven adapters
│   │       ├── storage/                      # File storage providers
│   │       │   ├── LocalFileStorageAdapter.java
│   │       │   └── S3FileStorageAdapter.java
│   │       ├── persistence/                  # Database repositories
│   │       │   ├── MongoDocumentAdapter.java
│   │       │   ├── MongoOutboxAdapter.java
│   │       │   └── JpaOutboxAdapter.java
│   │       └── messaging/                    # Kafka publishers
│   │           ├── EventPublisherAdapter.java
│   │           └── SagaReplyPublisherAdapter.java
│   └── config/                               # Spring configuration
│       ├── StorageConfig.java
│       ├── SagaRouteConfig.java
│       └── OutboxConfig.java
│
└── event/                                     # Domain events (moved from domain/event/)
    ├── DocumentStoredEvent.java
    ├── ProcessDocumentStorageCommand.java
    ├── CompensateDocumentStorageCommand.java
    ├── DocumentStorageReplyEvent.java
    ├── ProcessSignedXmlStorageCommand.java
    ├── CompensateSignedXmlStorageCommand.java
    ├── SignedXmlStorageReplyEvent.java
    ├── ProcessPdfStorageCommand.java
    ├── CompensatePdfStorageCommand.java
    └── PdfStorageReplyEvent.java
```

---

## 3. Port Definitions

### 3.1 Inbound Ports (Driving / Primary)

Inbound ports define what the service can do. They are implemented by application services and called by adapters.

#### DocumentStorageUseCase

Primary CRUD operations exposed via REST API:

```java
public interface DocumentStorageUseCase {
    StoredDocument storeDocument(byte[] content, String filename,
                                 DocumentType type, String invoiceId);
    Optional<StoredDocument> getDocument(String documentId);
    List<StoredDocument> getDocumentsByInvoice(String invoiceId);
    void deleteDocument(String documentId);
    boolean existsByInvoiceAndType(String invoiceId, DocumentType type);
}
```

#### SagaCommandUseCase

Saga orchestration operations consumed from Kafka:

```java
public interface SagaCommandUseCase {
    // Store signed PDF (step 8 of saga)
    void handleProcessCommand(ProcessDocumentStorageCommand command);
    // Store signed XML
    void handleProcessCommand(ProcessSignedXmlStorageCommand command);
    // Store unsigned PDF from MinIO (step 6 of saga)
    void handleProcessCommand(ProcessPdfStorageCommand command);
    // Compensation operations
    void handleCompensation(CompensateDocumentStorageCommand command);
    void handleCompensation(CompensateSignedXmlStorageCommand command);
    void handleCompensation(CompensatePdfStorageCommand command);
}
```

#### AuthenticationUseCase

Security operations for JWT authentication:

```java
public interface AuthenticationUseCase {
    AuthToken authenticate(String username, String password);
    AuthToken refreshToken(String refreshToken);
    void logout(String token);
    boolean validateToken(String token);
}
```

### 3.2 Outbound Ports (Driven / Secondary)

Outbound ports define what the service needs from external systems. They are implemented by infrastructure adapters.

#### StorageProviderPort

File storage abstraction for local filesystem and S3:

```java
public interface StorageProviderPort {
    StorageResult store(String documentId, InputStream content,
                        String originalFilename, long size);
    InputStream retrieve(String storageLocation) throws StorageException;
    void delete(String storageLocation) throws StorageException;
    boolean exists(String storageLocation);
}
```

#### DocumentRepositoryPort

MongoDB metadata repository:

```java
public interface DocumentRepositoryPort {
    StoredDocument save(StoredDocument document);
    Optional<StoredDocument> findById(String id);
    List<StoredDocument> findByInvoiceId(String invoiceId);
    Optional<StoredDocument> findByInvoiceIdAndDocumentType(
        String invoiceId, DocumentType type);
    void deleteById(String id);
    boolean existsByInvoiceIdAndDocumentType(
        String invoiceId, DocumentType type);
}
```

#### OutboxRepositoryPort

PostgreSQL outbox for Transactional Outbox pattern (from saga-commons):

```java
public interface OutboxRepositoryPort {
    OutboxEvent save(OutboxEvent event);
    void deleteById(String id);
}
```

#### MessagePublisherPort

Kafka publishing abstraction:

```java
public interface MessagePublisherPort {
    void publishEvent(DocumentStoredEvent event);
    void publishReply(DocumentStorageReplyEvent reply);
    void publishReply(SignedXmlStorageReplyEvent reply);
    void publishReply(PdfStorageReplyEvent reply);
}
```

---

## 4. Domain Services

### 4.1 FileStorageDomainService

Core storage logic coordinating storage provider and metadata repository:

```java
@Service
public class FileStorageDomainService {

    private final StorageProviderPort storageProvider;
    private final DocumentRepositoryPort documentRepository;

    @Transactional
    public StoredDocument store(byte[] content, String filename,
                                DocumentType type, String invoiceId) {
        // 1. Validate input
        // 2. Generate document ID
        // 3. Store file via provider
        // 4. Calculate checksum
        // 5. Create domain model
        // 6. Persist metadata
    }

    @Transactional(readOnly = true)
    public Optional<StoredDocument> retrieve(String documentId) { }

    public List<StoredDocument> findByInvoice(String invoiceId) { }

    @Transactional
    public void delete(String documentId) { }
}
```

### 4.2 SagaOrchestrationService

Handles saga commands and coordinates with message publisher:

```java
@Service
public class SagaOrchestrationService implements SagaCommandUseCase {

    private final FileStorageDomainService storageService;
    private final PdfDownloadDomainService pdfDownloadService;
    private final MessagePublisherPort messagePublisher;

    @Override
    @Transactional
    public void handleProcessCommand(ProcessDocumentStorageCommand command) {
        // 1. Idempotency check
        // 2. Download PDF
        // 3. Store document
        // 4. Publish event and reply via outbox
    }

    @Override
    @Transactional
    public void handleCompensation(CompensateDocumentStorageCommand command) {
        // Idempotent deletion
    }
}
```

---

## 5. Adapter Implementations

### 5.1 Inbound Adapters

#### DocumentStorageController

REST adapter implementing HTTP endpoints:

```java
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentStorageController {

    private final DocumentStorageUseCase documentStorageUseCase;

    @PostMapping
    public ResponseEntity<StoredDocument> uploadDocument(...) { }

    @GetMapping("/{id}")
    public ResponseEntity<StoredDocument> getDocument(@PathVariable String id) { }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable String id) { }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) { }
}
```

#### SagaCommandAdapter

Kafka/Camel messaging adapter:

```java
@Component
public class SagaCommandAdapter extends RouteBuilder {

    private final SagaCommandUseCase sagaCommandUseCase;

    @Override
    public void configure() {
        from("kafka:saga.command.document-storage")
            .unmarshal().json(JsonLibrary.Jackson, ProcessDocumentStorageCommand.class)
            .bean(sagaCommandUseCase, "handleProcessCommand");

        from("kafka:saga.compensation.document-storage")
            .unmarshal().json(JsonLibrary.Jackson, CompensateDocumentStorageCommand.class)
            .bean(sagaCommandUseCase, "handleCompensation");
    }
}
```

#### JwtAuthenticationAdapter

Security adapter for JWT authentication:

```java
public class JwtAuthenticationAdapter extends OncePerRequestFilter {

    private final AuthenticationUseCase authenticationUseCase;
    private final JwtTokenParser jwtParser;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {
        // JWT validation and authentication
    }
}
```

### 5.2 Outbound Adapters

#### LocalFileStorageAdapter

Local filesystem storage provider:

```java
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local")
public class LocalFileStorageAdapter implements StorageProviderPort {

    private final Path basePath;

    @Override
    public StorageResult store(String documentId, InputStream content,
                               String originalFilename, long size) {
        // Store in YYYY/MM/DD/UUID.ext structure
    }
}
```

#### S3FileStorageAdapter

S3/MinIO storage provider:

```java
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
public class S3FileStorageAdapter implements StorageProviderPort {

    private final S3Client s3Client;

    @Override
    public StorageResult store(String documentId, InputStream content,
                               String originalFilename, long size) {
        // Store in S3 with YYYY/MM/DD/UUID_filename.ext structure
    }
}
```

#### MongoDocumentAdapter

MongoDB repository adapter:

```java
@Component
public class MongoDocumentAdapter implements DocumentRepositoryPort {

    private final MongoTemplate mongoTemplate;

    @Override
    public StoredDocument save(StoredDocument document) {
        // Entity ↔ Domain mapping
    }
}
```

#### MessagePublisherAdapter

Kafka outbox publisher adapter:

```java
@Component
public class MessagePublisherAdapter implements MessagePublisherPort {

    private final OutboxService outboxService;

    @Override
    public void publishEvent(DocumentStoredEvent event) {
        OutboxEvent outbox = OutboxEvent.builder()
            .aggregateId(event.documentId())
            .aggregateType("StoredDocument")
            .eventType("DocumentStoredEvent")
            .payload(toJson(event))
            .topic("document.stored")
            .build();
        outboxService.save(outbox);
    }
}
```

---

## 6. Error Handling

### 6.1 Domain Exceptions

```java
public class DocumentNotFoundException extends DomainException {
    public DocumentNotFoundException(String documentId) {
        super("Document not found: " + documentId);
    }
}

public class StorageFailedException extends DomainException {
    public StorageFailedException(String reason) {
        super("Storage operation failed: " + reason);
    }
}

public class InvalidDocumentException extends DomainException {
    public InvalidDocumentException(String message) {
        super("Invalid document: " + message);
    }
}
```

### 6.2 Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(DocumentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(StorageFailedException.class)
    public ResponseEntity<ErrorResponse> handleStorageFailed(StorageFailedException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("STORAGE_ERROR", e.getMessage()));
    }
}
```

### 6.3 Saga Error Handling

Saga operations publish failure replies instead of throwing exceptions:

```java
@Override
@Transactional
public void handleProcessCommand(ProcessDocumentStorageCommand command) {
    try {
        // ... store logic
        publishSuccessReply(command);
    } catch (StorageFailedException e) {
        publishFailureReply(command, e.getMessage());
    }
}
```

---

## 7. Transaction Boundaries

- Domain services use `@Transactional` for write operations
- Read operations use `@Transactional(readOnly = true)`
- Outbox writes occur in the same transaction as domain operations
- Debezium CDC handles actual Kafka publishing asynchronously

```java
@Transactional
public StoredDocument store(byte[] content, String filename, ...) {
    // 1. Store file (non-transactional, handled via compensation)
    // 2. Save metadata (MongoDB)
    // 3. Save outbox events (PostgreSQL)
    // Transaction commits here
}
```

---

## 8. Migration Strategy

### 8.1 Phase 1: Create New Domain Structure

1. Create new packages under `domain/`:
   - `domain/port/inbound/`
   - `domain/port/outbound/`
   - `domain/service/`
   - `domain/exception/`

2. Define all port interfaces

3. Move `domain/model/` - keep existing classes

4. Create domain exceptions

### 8.2 Phase 2: Implement Domain Services

1. Create `FileStorageDomainService`
2. Create `SagaOrchestrationService` (merge three saga handlers)
3. Create `PdfDownloadDomainService`

### 8.3 Phase 3: Reorganize Infrastructure

1. Rename `infrastructure/persistence/` → `infrastructure/adapter/outbound/persistence/`
2. Rename `infrastructure/storage/` → `infrastructure/adapter/outbound/storage/`
3. Rename `infrastructure/messaging/` → `infrastructure/adapter/outbound/messaging/`
4. Rename `infrastructure/security/` → `infrastructure/adapter/inbound/security/`

### 8.4 Phase 4: Create Inbound Adapters

1. Move controllers to `infrastructure/adapter/inbound/rest/`
2. Create `SagaCommandAdapter` to replace `SagaRouteConfig`
3. Update `JwtAuthenticationFilter` → `JwtAuthenticationAdapter`

### 8.5 Phase 5: Configuration Updates

1. Update `@ComponentScan` if needed
2. Update `@ConditionalOnProperty` on storage adapters

### 8.6 Phase 6: Remove Old Structure

1. Delete `application/` directory
2. Update imports throughout codebase

### 8.7 Phase 7: Tests

1. Update test package structure
2. Create port interface mocks
3. Update integration tests

### 8.8 Migration File Map

| Old Location | New Location |
|-------------|--------------|
| `application/controller/` | `infrastructure/adapter/inbound/rest/` |
| `application/service/DocumentStorageService.java` | `domain/service/FileStorageDomainService.java` |
| `application/service/SagaCommandHandler.java` | `domain/service/SagaOrchestrationService.java` (merged) |
| `application/service/PdfStorageSagaCommandHandler.java` | `domain/service/SagaOrchestrationService.java` (merged) |
| `application/service/SignedXmlStorageSagaCommandHandler.java` | `domain/service/SagaOrchestrationService.java` (merged) |
| `application/service/PdfDownloadService.java` | `domain/service/PdfDownloadDomainService.java` |
| `domain/service/FileStorageProvider.java` | `domain/port/outbound/StorageProviderPort.java` |
| `infrastructure/storage/` | `infrastructure/adapter/outbound/storage/` |
| `infrastructure/persistence/` | `infrastructure/adapter/outbound/persistence/` |
| `infrastructure/messaging/` | `infrastructure/adapter/outbound/messaging/` |
| `infrastructure/config/SagaRouteConfig.java` | `infrastructure/adapter/inbound/messaging/SagaCommandAdapter.java` |
| `infrastructure/security/JwtAuthenticationFilter.java` | `infrastructure/adapter/inbound/security/JwtAuthenticationAdapter.java` |
| `domain/event/` | `event/` (moved to root) |

---

## 9. Testing Strategy

### 9.1 Domain Layer Unit Tests

Test domain services with mocked ports - no infrastructure:

```java
@ExtendWith(MockitoExtension.class)
class FileStorageDomainServiceTest {

    @Mock StorageProviderPort storageProvider;
    @Mock DocumentRepositoryPort documentRepository;

    @Test
    void storeDocument_success() {
        // Test with mocks
    }
}
```

### 9.2 Adapter Integration Tests

Test adapters against real infrastructure using Testcontainers:

```java
@Testcontainers
class LocalFileStorageAdapterTest {

    @TempDir Path tempDir;

    @Test
    void storeAndRetrieve_success() {
        // Test with real filesystem
    }
}
```

### 9.3 Test Coverage Targets

| Layer | Target |
|-------|--------|
| Domain Services | 90%+ |
| Domain Models | 80%+ |
| Adapters | 70%+ |
| Controllers | 80%+ |
| Overall | 80%+ |

---

## 10. Configuration

### 10.1 No Changes Required

- Maven dependencies remain unchanged
- Environment variables remain unchanged
- Kafka topics remain unchanged
- Database schemas remain unchanged
- REST API endpoints remain unchanged

### 10.2 Component Scanning

Spring's default `@ComponentScan` will find all beans. Ports are interfaces (not scanned), adapters use `@Component` (automatically scanned).

### 10.3 Dependency Injection

Only one storage adapter is active at runtime due to `@ConditionalOnProperty`:

```yaml
app:
  storage:
    provider: local  # or 's3'
```

---

## 11. Validation Criteria

The migration is successful when:

1. ✅ All domain services have 90%+ test coverage with mocked ports
2. ✅ All adapters have integration tests with Testcontainers
3. ✅ No references to `application/` package remain
4. ✅ All dependencies point inward toward domain
5. ✅ REST API endpoints return identical responses
6. ✅ Saga commands process end-to-end without errors
7. ✅ JWT authentication works as before
8. ✅ Both local and S3 storage providers work correctly
9. ✅ Outbox events are published to Kafka
10. ✅ All tests pass (unit + integration)

---

## 12. Rollback Plan

If issues arise after migration:

1. Revert to previous commit before migration
2. No data migration required (no schema changes)
3. No configuration changes required
4. Kafka topic subscriptions remain unchanged
5. Service resumes normal operation immediately

---

## Appendix: Hexagonal Architecture Diagram

```
                    ┌─────────────────────────────────────────┐
                    │           Inbound Adapters              │
                    │  (Controllers, Kafka Consumers, JWT)    │
                    └─────────────────┬───────────────────────┘
                                      │
                    ┌─────────────────▼───────────────────────┐
                    │         Inbound Ports (Use Cases)       │
                    │  DocumentStorageUseCase                 │
                    │  SagaCommandUseCase                     │
                    │  AuthenticationUseCase                  │
                    └─────────────────┬───────────────────────┘
                                      │
                    ┌─────────────────▼───────────────────────┐
                    │           Domain Services               │
                    │  FileStorageDomainService               │
                    │  SagaOrchestrationService               │
                    │  + Domain Models & Exceptions           │
                    └─────────────────┬───────────────────────┘
                                      │
                    ┌─────────────────▼───────────────────────┐
                    │         Outbound Ports                  │
                    │  StorageProviderPort                    │
                    │  DocumentRepositoryPort                 │
                    │  OutboxRepositoryPort                   │
                    │  MessagePublisherPort                   │
                    └─────────────────┬───────────────────────┘
                                      │
                    ┌─────────────────▼───────────────────────┐
                    │          Outbound Adapters              │
                    │  Local/S3 Storage, MongoDB, PostgreSQL  │
                    │  Kafka Publishers                       │
                    └─────────────────────────────────────────┘
```

---

**Document Version:** 1.0
**Status:** Approved
**Next Step:** Create implementation plan using writing-plans skill
