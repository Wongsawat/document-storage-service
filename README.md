# Document Storage Service

Microservice for storing and managing documents (PDFs, XML attachments) with MongoDB metadata storage and pluggable storage backends. Participates in the **Saga Orchestrator** as three steps: `PDF_STORAGE`, `SIGNEDXML_STORAGE`, and `STORE_DOCUMENT`.

## Overview

The Document Storage Service:

- **Stores** documents with checksum verification (SHA-256)
- **Supports** multiple storage backends (local filesystem, AWS S3)
- **Manages** document metadata in MongoDB
- **Provides** REST API for upload, download, delete operations
- **Participates** in Saga Orchestrator as 3 steps (PDF_STORAGE, SIGNEDXML_STORAGE, STORE_DOCUMENT)
- **Implements** Transactional Outbox pattern for reliable event publishing
- **Tracks** document relationships to invoices
- **Authenticates** via JWT tokens with token blacklist support

## Architecture

### Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Metadata DB | MongoDB 7 |
| Outbox DB | PostgreSQL 16 |
| Messaging | Apache Kafka (via Apache Camel 4.14.4) |
| Storage | Local FS / AWS S3 |
| Saga | saga-commons (Transactional Outbox + Debezium CDC) |
| Service Discovery | Netflix Eureka |
| Security | Spring Security 6 + JWT (HS256) |
| Resilience | Resilience4j (Circuit Breaker, Retry) |
| Metrics | Micrometer + Prometheus |

### Domain Model

**Aggregate Root:**
- `StoredDocument` - Document metadata with checksum verification (immutable with `withExpiresAt()` copy method)

**Value Objects:**
- `DocumentType` - `INVOICE_PDF`, `INVOICE_XML`, `SIGNED_XML`, `UNSIGNED_PDF`, `ATTACHMENT`, `OTHER`
- `StorageResult` - Record containing storage path, URL, and file size
- `AuthToken` - Authentication token value object

**Ports (Interfaces):**
- `DocumentRepositoryPort` - Repository interface (domain-defined)
- `StorageProviderPort` - Storage abstraction (outbound port)
- `PdfDownloadPort` - PDF download abstraction (outbound port)
- `MessagePublisherPort` - Event publishing abstraction (outbound port)
- `OutboxRepositoryPort` - Outbox persistence port (outbound port)
- `MetricsPort` - Metrics publishing port (outbound port)

**Domain Events:**
- `DocumentStoredEvent` - Published after successful document storage
- `ProcessDocumentStorageCommand` - Saga command for STORE_DOCUMENT step
- `CompensateDocumentStorageCommand` - Saga compensation for STORE_DOCUMENT
- `DocumentStorageReplyEvent` - Saga reply for STORE_DOCUMENT step
- `ProcessPdfStorageCommand` - Saga command for PDF_STORAGE step
- `CompensatePdfStorageCommand` - Saga compensation for PDF_STORAGE
- `PdfStorageReplyEvent` - Saga reply for PDF_STORAGE step
- `ProcessSignedXmlStorageCommand` - Saga command for SIGNEDXML_STORAGE step
- `CompensateSignedXmlStorageCommand` - Saga compensation for SIGNEDXML_STORAGE
- `SignedXmlStorageReplyEvent` - Saga reply for SIGNEDXML_STORAGE step

### Storage Backends

#### Local Filesystem Storage
- **Structure**: `/var/documents/YYYY/MM/DD/UUID.ext`
- **URL Format**: `http://localhost:8084/api/v1/documents/{id}`
- **Use Case**: Development, small deployments

#### AWS S3 Storage
- **Structure**: `s3://bucket/YYYY/MM/DD/UUID_filename.ext`
- **URL Format**: `https://bucket.s3.amazonaws.com/YYYY/MM/DD/UUID_filename.ext`
- **Supports**: MinIO/Ceph via `S3_ENDPOINT` and `S3_PATH_STYLE_ACCESS`
- **Use Case**: Production, scalable deployments

## Saga Integration

This service participates in **three** saga steps:

```
PROCESS_TAX_INVOICE → SIGN_XML → SIGNEDXML_STORAGE → GENERATE_TAX_INVOICE_PDF → PDF_STORAGE → SIGN_PDF → STORE_DOCUMENT → SEND_EBMS
                                                      └────────────┘           └─────────┘          └──────────────┘
                                                   (step 4)                  (step 6)               (step 8)
```

### SIGNEDXML_STORAGE Flow (Step 4)

```
Orchestrator                          Document Storage Service
────────────                          ────────────────────────
saga.command.signedxml-storage   →    SagaCommandAdapter (Camel)
                                        → SagaCommandUseCase.handleSignedXmlStorageCommand()
                                          1. Store signed XML content
                                          2. Publish SUCCESS reply (outbox)
saga.reply.signedxml-storage     ←    MessagePublisherAdapter (via Debezium CDC)
```

### PDF_STORAGE Flow (Step 6 - Tax Invoice Only)

```
Orchestrator                          Document Storage Service
────────────                          ────────────────────────
saga.command.pdf-storage         →    SagaCommandAdapter (Camel)
                                        → SagaCommandUseCase.handlePdfStorageCommand()
                                          1. Idempotency check (findByInvoiceIdAndDocumentType)
                                          2. Download unsigned PDF from MinIO (pdfUrl)
                                          3. Store file + MongoDB metadata (UNSIGNED_PDF)
                                          4. Publish SUCCESS reply with storedDocumentUrl (outbox)
saga.reply.pdf-storage           ←    MessagePublisherAdapter (via Debezium CDC)
```

### STORE_DOCUMENT Flow (Step 8)

```
Orchestrator                          Document Storage Service
────────────                          ────────────────────────
saga.command.document-storage    →    SagaCommandAdapter (Camel)
                                        → SagaCommandUseCase.handleDocumentStorageCommand()
                                          1. Idempotency check (findByInvoiceId)
                                          2. Download signed PDF from signedPdfUrl
                                          3. Store file + MongoDB metadata (INVOICE_PDF)
                                          4. Publish DocumentStoredEvent (outbox)
                                          5. Publish SUCCESS reply (outbox)
saga.reply.document-storage      ←    MessagePublisherAdapter (via Debezium CDC)
```

### Compensation

All three saga steps support compensation via the corresponding `Compensate*` commands:
- **SIGNEDXML_STORAGE**: Deletes stored signed XML documents
- **PDF_STORAGE**: Deletes stored unsigned PDFs (UNSIGNED_PDF type only)
- **STORE_DOCUMENT**: Deletes stored signed PDFs (INVOICE_PDF type only, scoped to avoid deleting other documents)

### Transactional Outbox Pattern

`MessagePublisherAdapter` writes events to the `outbox_events` table in PostgreSQL within the same `@Transactional` boundary as the saga command processing. Debezium CDC captures changes from the outbox table and publishes them to Kafka, ensuring exactly-once delivery semantics.

### Kafka Topics

| Topic | Direction | Description |
|-------|-----------|-------------|
| `saga.command.document-storage` | Consumed (Camel) | STORE_DOCUMENT commands from orchestrator |
| `saga.compensation.document-storage` | Consumed (Camel) | STORE_DOCUMENT compensation from orchestrator |
| `saga.command.pdf-storage` | Consumed (Camel) | PDF_STORAGE commands from orchestrator |
| `saga.compensation.pdf-storage` | Consumed (Camel) | PDF_STORAGE compensation from orchestrator |
| `saga.command.signedxml-storage` | Consumed (Camel) | SIGNEDXML_STORAGE commands from orchestrator |
| `saga.compensation.signedxml-storage` | Consumed (Camel) | SIGNEDXML_STORAGE compensation from orchestrator |
| `saga.reply.document-storage` | Published (Outbox) | STORE_DOCUMENT replies to orchestrator |
| `saga.reply.pdf-storage` | Published (Outbox) | PDF_STORAGE replies to orchestrator |
| `saga.reply.signedxml-storage` | Published (Outbox) | SIGNEDXML_STORAGE replies to orchestrator |
| `document.stored` | Published (Outbox) | Downstream notification of stored documents |
| `document-storage.dlq` | Dead Letter | Failed messages after 3 retries |

## REST API

### Document Storage Endpoints

#### Upload Document
```bash
POST /api/v1/documents
Content-Type: multipart/form-data
Authorization: Bearer <access_token>

Parameters:
- file: MultipartFile (required)
- documentType: DocumentType (optional, default: OTHER)
- invoiceId: String (optional)
- invoiceNumber: String (optional)

Response: 201 Created
{
  "documentId": "uuid",
  "fileName": "INV-2025-001_invoice.pdf",
  "url": "http://localhost:8084/api/v1/documents/uuid",
  "fileSize": 125000,
  "checksum": "sha256hex..."
}
```

#### Download Document
```bash
GET /api/v1/documents/{id}
Authorization: Bearer <access_token>

Response: 200 OK
Content-Disposition: attachment; filename="INV-2025-001_invoice.pdf"
Content-Type: application/pdf

[Binary PDF content]
```

#### Get Document Metadata
```bash
GET /api/v1/documents/{id}/metadata
Authorization: Bearer <access_token>

Response: 200 OK
{
  "id": "uuid",
  "fileName": "INV-2025-001_invoice.pdf",
  "contentType": "application/pdf",
  "fileSize": 125000,
  "checksum": "sha256hex...",
  "documentType": "INVOICE_PDF",
  "createdAt": "2025-12-03T10:30:00",
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2025-001"
}
```

#### Delete Document
```bash
DELETE /api/v1/documents/{id}
Authorization: Bearer <access_token>

Response: 204 No Content
```

#### Get Documents by Invoice
```bash
GET /api/v1/documents/invoice/{invoiceId}
Authorization: Bearer <access_token>

Response: 200 OK
[
  {
    "id": "uuid",
    "fileName": "INV-2025-001_invoice.pdf",
    "url": "http://localhost:8084/api/v1/documents/uuid",
    "fileSize": 125000,
    "documentType": "INVOICE_PDF",
    "createdAt": "2025-12-03T10:30:00"
  }
]
```

### Authentication Endpoints

#### Register User
```bash
POST /api/v1/auth/register
Content-Type: application/json

Request:
{
  "username": "user@example.com",
  "password": "SecurePass123!",
  "role": "DOCUMENT_READ"
}

Response: 201 Created
{
  "id": "uuid",
  "username": "user@example.com",
  "role": "DOCUMENT_READ"
}
```

#### Login
```bash
POST /api/v1/auth/login
Content-Type: application/json

Request:
{
  "username": "user@example.com",
  "password": "SecurePass123!"
}

Response: 200 OK
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400000
}
```

#### Refresh Token
```bash
POST /api/v1/auth/refresh
Content-Type: application/json

Request:
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}

Response: 200 OK
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400000
}
```

#### Logout
```bash
POST /api/v1/auth/logout
Content-Type: application/json
Authorization: Bearer <access_token>

Request:
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}

Response: 204 No Content
```

#### Validate Token
```bash
POST /api/v1/auth/validate
Content-Type: application/json
Authorization: Bearer <access_token>

Response: 200 OK
{
  "valid": true,
  "username": "user@example.com",
  "expiresAt": "2025-12-04T10:30:00"
}
```

## MongoDB Collections

### documents Collection
```javascript
{
  _id: "uuid",
  fileName: "INV-2025-001_invoice.pdf",
  contentType: "application/pdf",
  storagePath: "/var/documents/2025/12/03/uuid.pdf",
  storageUrl: "http://localhost:8084/api/v1/documents/uuid",
  fileSize: 125000,
  checksum: "sha256hex...",
  documentType: "INVOICE_PDF",
  createdAt: ISODate("2025-12-03T10:30:00Z"),
  expiresAt: null,
  invoiceId: "uuid",
  invoiceNumber: "INV-2025-001"
}
```

### token_blacklist Collection
```javascript
{
  _id: "uuid",
  tokenId: "jti-claim-from-jwt",
  revokedAt: ISODate("2025-12-03T10:30:00Z"),
  expiresAt: ISODate("2025-12-10T10:30:00Z")
}
```

### Indexes
**documents:**
- `fileName` - For filename lookups
- `documentType` - For type-based queries
- `invoiceId` - For invoice relationships
- `invoiceNumber` - For invoice number searches
- `expiresAt` - For TTL-based cleanup
- `createdAt` - For time-range queries
- `{tokenId: 1}, {expiresAt: 1}` - For token blacklist TTL

## PostgreSQL Tables

### outbox_events Table
```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,
    retry_count INT DEFAULT 0
);
```

Flyway migration: `V1__create_outbox_events_table.sql`

### users Table
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

Flyway migration: `V2__create_users_table.sql`

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL host (outbox, users) | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | PostgreSQL database | `documentstorage_db` |
| `DB_USERNAME` | PostgreSQL username | `postgres` |
| `DB_PASSWORD` | PostgreSQL password | `postgres` |
| `MONGODB_HOST` | MongoDB host | `localhost` |
| `MONGODB_PORT` | MongoDB port | `27017` |
| `MONGODB_DATABASE` | MongoDB database name | `document_storage` |
| `KAFKA_BROKERS` | Kafka servers | `localhost:9092` |
| `STORAGE_PROVIDER` | Storage backend (`local` or `s3`) | `local` |
| `LOCAL_STORAGE_PATH` | Local filesystem path | `/var/documents` |
| `STORAGE_BASE_URL` | Base URL for documents | `http://localhost:8084/api/v1/documents` |
| `S3_BUCKET_NAME` | S3 bucket name | - |
| `AWS_REGION` | AWS region | `us-east-1` |
| `S3_ENDPOINT` | Custom S3 endpoint (MinIO, Ceph) | (empty) |
| `S3_PATH_STYLE_ACCESS` | Use path-style S3 access | `false` |
| `EUREKA_SERVER` | Eureka URL | `http://localhost:8761/eureka/` |

### Local Filesystem Configuration

```yaml
app:
  storage:
    provider: local
    local:
      base-path: /var/documents
      base-url: http://localhost:8084/api/v1/documents
```

### AWS S3 Configuration

```yaml
app:
  storage:
    provider: s3
    s3:
      bucket-name: invoice-documents
      region: us-east-1
```

### JWT Configuration (Development)

In `application-dev.yml`, use the placeholder to auto-generate a secure 512-bit secret at startup:

```yaml
app:
  security:
    jwt:
      secret: GENERATE_DEV_SECRET  # Auto-generates random 512-bit secret
      expiration: 86400000  # 24 hours
      refresh-expiration: 604800000  # 7 days
```

**Note:** In production, always set a secure JWT secret via environment variable or secure vault.

## Running the Service

### Prerequisites
1. MongoDB 7+ running on `localhost:27017`
2. PostgreSQL 16+ running with database `documentstorage_db`
3. Kafka broker running on `localhost:9092`
4. saga-commons library installed: `cd ../../saga-commons && mvn clean install`
5. (For local storage) Directory with write permissions
6. (For S3 storage) AWS credentials with S3 access

### Build
```bash
mvn clean package
```

### Run Locally
```bash
DB_HOST=localhost DB_PORT=5432 DB_NAME=documentstorage_db \
  MONGODB_HOST=localhost KAFKA_BROKERS=localhost:9092 \
  STORAGE_PROVIDER=local LOCAL_STORAGE_PATH=/tmp/documents \
  mvn spring-boot:run
```

### Run with Docker

#### Local Filesystem Storage
```bash
docker build -t document-storage-service:latest .

docker run -p 8084:8084 \
  -e MONGODB_HOST=mongo \
  -e DB_HOST=postgres \
  -e DB_NAME=documentstorage_db \
  -e KAFKA_BROKERS=kafka:29092 \
  -e STORAGE_PROVIDER=local \
  -v /host/documents:/var/documents \
  document-storage-service:latest
```

#### AWS S3 Storage
```bash
docker run -p 8084:8084 \
  -e MONGODB_HOST=mongo \
  -e DB_HOST=postgres \
  -e DB_NAME=documentstorage_db \
  -e KAFKA_BROKERS=kafka:29092 \
  -e STORAGE_PROVIDER=s3 \
  -e S3_BUCKET_NAME=invoice-documents \
  -e AWS_REGION=us-east-1 \
  document-storage-service:latest
```

## Security Features

### JWT Authentication
- **HS256 algorithm** with 256-bit minimum secret key requirement
- **Access tokens** expire after 24 hours (configurable)
- **Refresh tokens** expire after 7 days (configurable)
- **Token blacklist** stored in MongoDB for logout/revocation
- **Rate limiting** on authentication endpoints (10 requests per minute per IP)

### Checksum Verification
- **SHA-256 checksum** calculated on upload
- **Prevents** tampered documents from being served
- **Verified** before storage and on download

### Storage Path Security
- **UUID-based filenames** prevent path traversal
- **Path validation** ensures all paths resolve within base directory
- **Date-based directories** organize storage

## Project Structure

This service follows **Hexagonal Architecture** (Ports and Adapters) with **Domain-Driven Design** patterns.

```
src/main/java/com/wpanther/storage/
├── DocumentStorageServiceApplication.java
├── domain/
│   ├── model/
│   │   ├── StoredDocument.java          # Aggregate root (immutable, Builder pattern)
│   │   ├── DocumentType.java            # Enum of document types
│   │   ├── StorageResult.java           # Record for storage results
│   │   ├── StorageException.java        # Domain exception
│   │   └── AuthToken.java               # Auth token value object
│   ├── repository/
│   │   └── DocumentRepositoryPort.java  # Repository port interface
│   ├── exception/
│   │   ├── DomainException.java
│   │   ├── DocumentNotFoundException.java
│   │   ├── InvalidDocumentException.java
│   │   └── StorageFailedException.java
│   └── util/
│       └── ContentTypeUtil.java
├── application/
│   ├── dto/event/                       # Kafka wire DTOs
│   │   ├── DocumentStoredEvent.java
│   │   ├── ProcessDocumentStorageCommand.java
│   │   ├── CompensateDocumentStorageCommand.java
│   │   ├── DocumentStorageReplyEvent.java
│   │   ├── ProcessPdfStorageCommand.java
│   │   ├── CompensatePdfStorageCommand.java
│   │   ├── PdfStorageReplyEvent.java
│   │   ├── ProcessSignedXmlStorageCommand.java
│   │   ├── CompensateSignedXmlStorageCommand.java
│   │   └── SignedXmlStorageReplyEvent.java
│   ├── port/out/                        # Outbound ports
│   │   ├── StorageProviderPort.java     # Storage abstraction
│   │   ├── PdfDownloadPort.java        # HTTP PDF download port
│   │   ├── MessagePublisherPort.java   # Event publishing port
│   │   ├── OutboxRepositoryPort.java   # Outbox repository port
│   │   └── MetricsPort.java            # Metrics publishing port
│   └── usecase/                         # Application use cases
│       ├── DocumentStorageUseCase.java  # Document storage orchestration
│       ├── FileStorageDomainService.java # Core domain logic
│       ├── SagaCommandUseCase.java      # Saga command handlers (all 3 steps)
│       ├── SagaOrchestrationService.java # Saga compensation orchestration
│       └── AuthenticationUseCase.java   # JWT auth use case
├── infrastructure/
│   ├── adapter/
│   │   ├── in/                          # Inbound adapters
│   │   │   ├── rest/
│   │   │   │   ├── DocumentStorageController.java
│   │   │   │   ├── AuthenticationController.java
│   │   │   │   ├── DocumentValidator.java
│   │   │   │   └── config/
│   │   │   │       └── ApiVersion.java
│   │   │   ├── messaging/
│   │   │   │   └── SagaCommandAdapter.java  # Camel routes (6 saga routes)
│   │   │   ├── scheduler/
│   │   │   │   └── OutboxReconciliationService.java
│   │   │   └── security/
│   │   │       ├── JwtService.java
│   │   │       ├── TokenBlacklistService.java
│   │   │       ├── JwtAuthenticationAdapter.java
│   │   │       ├── RateLimitingFilter.java
│   │   │       ├── JwtAccessDeniedHandler.java
│   │   │       ├── JwtAuthenticationEntryPoint.java
│   │   │       ├── DocumentStorageUserDetailsService.java
│   │   │       ├── exception/
│   │   │       │   ├── SecurityException.java
│   │   │       │   ├── AuthenticationFailedException.java
│   │   │       │   ├── AuthorizationFailedException.java
│   │   │       │   └── InvalidTokenException.java
│   │   │       └── config/
│   │   │           └── SecurityProperties.java
│   │   └── out/                         # Outbound adapters
│   │       ├── storage/
│   │       │   ├── LocalFileStorageAdapter.java
│   │       │   └── S3FileStorageAdapter.java
│   │       ├── http/
│   │       │   └── PdfDownloadAdapter.java
│   │       ├── messaging/
│   │       │   └── MessagePublisherAdapter.java
│   │       ├── persistence/
│   │       │   ├── StoredDocumentEntity.java  # MongoDB entity
│   │       │   ├── StoredDocumentMapper.java
│   │       │   ├── MongoDocumentAdapter.java  # Implements DocumentRepositoryPort
│   │       │   └── outbox/
│   │       │       ├── OutboxEventEntity.java  # PostgreSQL JPA entity
│   │       │       ├── SpringDataOutboxRepository.java
│   │       │       └── JpaOutboxEventRepository.java
│   │       └── persistence/
│   │           └── MongoOutboxEventAdapter.java  # Implements OutboxRepositoryPort
│   └── config/
│       ├── SecurityConfig.java         # Spring Security 6 JWT
│       ├── DevJwtSecretGenerator.java  # Auto-generates JWT secret in dev mode
│       ├── JwtConfigValidator.java     # Validates JWT config at startup
│       ├── OutboxConfig.java
│       ├── MongoTransactionConfig.java
│       ├── MetricsConfig.java
│       ├── resilience/
│       │   └── ResilienceConfig.java  # Circuit breakers, retry
│       ├── health/
│       │   └── OutboxHealthIndicator.java
│       └── security/
│           └── DevJwtSecretGenerator.java

src/main/resources/
├── application.yml
├── application-dev.yml
└── db/migration/
    ├── V1__create_outbox_events_table.sql
    └── V2__create_users_table.sql
```

## Testing

### Running Unit Tests

```bash
# Run all tests
mvn clean test

# Run specific test class
mvn test -Dtest=SagaCommandAdapterTest

# Run specific test method
mvn test -Dtest=SagaCommandAdapterTest#shouldHandlePdfStorageCommand
```

### Test Coverage

The service has 313 tests with JaCoCo coverage reporting. Run tests with coverage:

```bash
mvn clean verify
```

Coverage report: `target/site/jacoco/index.html`

### Test Configuration

Test configuration is in `src/test/resources/application-test.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `spring.datasource.url` | `jdbc:h2:mem:testdb` | In-memory H2 database |
| `spring.data.mongodb.uri` | `mongodb://localhost:27017/document_storage_test` | MongoDB connection |
| `kafka.bootstrap-servers` | `localhost:9092` | Kafka broker address |
| `app.storage.local.base-path` | `${java.io.tmpdir}/test-documents` | Local storage path |
| `app.security.jwt.secret` | (test secret) | JWT secret for tests |

## Monitoring

### Actuator Endpoints
- `/actuator/health` - Service health (MongoDB + PostgreSQL + Outbox)
- `/actuator/info` - Service information
- `/actuator/metrics` - Application metrics (Prometheus format)
- `/actuator/prometheus` - Prometheus scrape endpoint

### Health Check
```bash
curl http://localhost:8084/actuator/health
```

Response:
```json
{
  "status": "UP",
  "components": {
    "mongo": { "status": "UP" },
    "db": { "status": "UP" },
    "outbox": {
      "status": "UP",
      "details": {
        "pendingEvents": 0,
        "failedEvents": 0
      }
    }
  }
}
```

### Metrics

Key metrics exposed:
- `document.storage.operations.count` - Document storage operations by type
- `document.storage.operations.duration` - Operation duration
- `saga.commands.processed` - Saga commands processed by status
- `outbox.events.published` - Outbox events published to Kafka

## Related Services

- **Upstream**: Orchestrator Service (port 8093) - sends saga commands for all 3 steps
- **Upstream**: Tax Invoice PDF Generation Service (port 8089) - provides unsigned PDF via MinIO
- **Downstream**: Notification Service (port 8085) - consumes `document.stored` events
- **Dependency**: saga-commons library (`cd ../../saga-commons && mvn clean install`)

## License

MIT License

## Contact

Maintained by wpanther (rabbit_roger@yahoo.com)
