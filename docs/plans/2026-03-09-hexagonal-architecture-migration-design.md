# Hexagonal Architecture Migration Design (Canonical Alignment)

**Date:** 2026-03-09
**Service:** document-storage-service (port 8084)
**Type:** Refactor — package rename + relocation + new `PdfDownloadPort` interface, no logic changes
**Strategy:** Phase-by-phase incremental (one commit per logical group, tests green after each)

---

## Context

The document-storage-service already uses hexagonal architecture (`infrastructure/adapter/inbound/`, `infrastructure/adapter/outbound/`), but with naming and structural differences from the **canonical layout** established by the other services. This migration aligns all packages with the canonical target:

- `domain/` ← `application/` ← `infrastructure/` (strict dependency rule)
- `application/usecase/` for use-case interfaces and their implementations
- `domain/repository/` for domain-owned output ports
- `application/port/out/` for non-domain outbound ports
- `application/dto/event/` for Kafka wire DTOs
- `infrastructure/adapter/in/` and `infrastructure/adapter/out/` (not `inbound/outbound`)
- `infrastructure/config/` with concern-based sub-packages

**Remaining gaps:**

| Current | Target | Change |
|---|---|---|
| `domain/event/` (10 Kafka DTOs) | `application/dto/event/` | DTOs are not domain events |
| `domain/port/inbound/` (3 interfaces) | `application/usecase/` | Use-case interfaces |
| `domain/port/outbound/DocumentRepositoryPort` | `domain/repository/` | Domain-owned port |
| `domain/port/outbound/` (3 other ports) | `application/port/out/` | Application-layer ports |
| `domain/service/FileStorageDomainService` | `application/usecase/` | Implements use case |
| `domain/service/SagaOrchestrationService` | `application/usecase/` | Implements use case |
| `domain/service/PdfDownloadDomainService` | `infrastructure/adapter/out/http/PdfDownloadAdapter` | HTTP client = infrastructure |
| `infrastructure/adapter/inbound/` | `infrastructure/adapter/in/` | Canonical rename |
| `infrastructure/adapter/outbound/` | `infrastructure/adapter/out/` | Canonical rename |
| `infrastructure/messaging/OutboxReconciliationService` | `infrastructure/adapter/in/scheduler/` | `@Scheduled` = inbound adapter |
| `adapter/inbound/security/config/SecurityConfig` | `infrastructure/config/security/` | Config belongs in config layer |
| `adapter/inbound/security/config/JwtConfigValidator` | `infrastructure/config/security/` | Config belongs in config layer |
| `infrastructure/config/` flat (4 items) | `infrastructure/config/<concern>/` | Sub-package split |

---

## Target Package Structure

```
com.wpanther.storage/
├── domain/
│   ├── model/                              # unchanged
│   │   ├── StoredDocument.java
│   │   ├── DocumentType.java
│   │   ├── AuthToken.java
│   │   ├── StorageException.java
│   │   └── StorageResult.java
│   ├── repository/                         # NEW — split from domain/port/outbound/
│   │   └── DocumentRepositoryPort.java
│   ├── exception/                          # unchanged
│   │   ├── DomainException.java
│   │   ├── DocumentNotFoundException.java
│   │   ├── InvalidDocumentException.java
│   │   └── StorageFailedException.java
│   └── util/                               # unchanged
│       └── ContentTypeUtil.java
│   # domain/port/, domain/service/, domain/event/ FULLY REMOVED
│
├── application/
│   ├── usecase/                            # MERGED from domain/port/inbound/ + domain/service/
│   │   ├── AuthenticationUseCase.java      # MOVED from domain/port/inbound/
│   │   ├── DocumentStorageUseCase.java     # MOVED from domain/port/inbound/
│   │   ├── SagaCommandUseCase.java         # MOVED from domain/port/inbound/
│   │   ├── FileStorageDomainService.java   # MOVED from domain/service/ (implements DocumentStorageUseCase)
│   │   └── SagaOrchestrationService.java   # MOVED from domain/service/ (implements SagaCommandUseCase)
│   ├── port/out/
│   │   ├── MessagePublisherPort.java       # MOVED from domain/port/outbound/
│   │   ├── OutboxRepositoryPort.java       # MOVED from domain/port/outbound/
│   │   ├── StorageProviderPort.java        # MOVED from domain/port/outbound/
│   │   └── PdfDownloadPort.java            # NEW — HTTP download abstraction
│   └── dto/
│       └── event/                          # MOVED from domain/event/
│           ├── ProcessDocumentStorageCommand.java
│           ├── CompensateDocumentStorageCommand.java
│           ├── DocumentStorageReplyEvent.java
│           ├── DocumentStoredEvent.java
│           ├── ProcessPdfStorageCommand.java
│           ├── CompensatePdfStorageCommand.java
│           ├── PdfStorageReplyEvent.java
│           ├── ProcessSignedXmlStorageCommand.java
│           ├── CompensateSignedXmlStorageCommand.java
│           └── SignedXmlStorageReplyEvent.java
│
└── infrastructure/
    ├── adapter/
    │   ├── in/                             # RENAMED from adapter/inbound/
    │   │   ├── messaging/
    │   │   │   └── SagaCommandAdapter.java
    │   │   ├── rest/
    │   │   │   ├── AuthenticationController.java
    │   │   │   ├── DocumentStorageController.java
    │   │   │   ├── DocumentValidator.java
    │   │   │   └── config/ApiVersion.java
    │   │   ├── security/                   # MOVED from adapter/inbound/security/ (SecurityConfig extracted)
    │   │   │   ├── DocumentStorageUserDetailsService.java
    │   │   │   ├── JwtAccessDeniedHandler.java
    │   │   │   ├── JwtAuthenticationAdapter.java
    │   │   │   ├── JwtAuthenticationEntryPoint.java
    │   │   │   ├── JwtService.java
    │   │   │   ├── RateLimitingFilter.java
    │   │   │   ├── TokenBlacklistService.java
    │   │   │   └── exception/
    │   │   │       ├── AuthenticationFailedException.java
    │   │   │       ├── AuthorizationFailedException.java
    │   │   │       ├── InvalidTokenException.java
    │   │   │       └── SecurityException.java
    │   │   └── scheduler/                  # NEW — moved from infrastructure/messaging/
    │   │       └── OutboxReconciliationService.java
    │   └── out/                            # RENAMED from adapter/outbound/
    │       ├── messaging/
    │       │   └── MessagePublisherAdapter.java
    │       ├── persistence/
    │       │   ├── DocumentRepositoryAdapter.java
    │       │   ├── MongoDocumentAdapter.java
    │       │   ├── MongoOutboxEventAdapter.java
    │       │   ├── StoredDocumentEntity.java
    │       │   ├── StoredDocumentMapper.java
    │       │   └── outbox/
    │       │       ├── JpaOutboxEventRepository.java
    │       │       ├── OutboxEventEntity.java
    │       │       └── SpringDataOutboxRepository.java
    │       ├── storage/
    │       │   ├── LocalFileStorageAdapter.java
    │       │   └── S3FileStorageAdapter.java
    │       └── http/                       # NEW
    │           └── PdfDownloadAdapter.java # RENAMED from PdfDownloadDomainService
    └── config/
        ├── metrics/
        │   ├── MetricsConfig.java
        │   └── DocumentStorageMetricsService.java
        ├── outbox/
        │   └── OutboxConfig.java
        ├── resilience/
        │   └── ResilienceConfig.java
        └── security/
            ├── SecurityConfig.java         # MOVED from adapter/inbound/security/config/
            └── JwtConfigValidator.java     # MOVED from adapter/inbound/security/config/
```

---

## Component Design

### Use-Case Merge (`application/usecase/`)

`domain/port/inbound/` interfaces and `domain/service/` implementations co-locate in `application/usecase/`. Package declarations updated, no logic changes:

- `FileStorageDomainService` implements `DocumentStorageUseCase` — no change to method bodies
- `SagaOrchestrationService` implements `SagaCommandUseCase` — field type changes from `PdfDownloadDomainService` to `PdfDownloadPort` (interface injection)
- `AuthenticationUseCase` moves as-is (implementation is in `infrastructure/adapter/in/security/`)

### `PdfDownloadPort` + `PdfDownloadAdapter`

New interface in `application/port/out/`:

```java
package com.wpanther.storage.application.port.out;

public interface PdfDownloadPort {
    byte[] downloadPdf(String url);
}
```

`PdfDownloadDomainService` is renamed to `PdfDownloadAdapter`, moved to `infrastructure/adapter/out/http/`, and updated:
- Package: `infrastructure.adapter.out.http`
- Class name: `PdfDownloadDomainService` → `PdfDownloadAdapter`
- `implements PdfDownloadPort` added
- All method logic unchanged

`SagaOrchestrationService` field change:
```java
// Before:
private final PdfDownloadDomainService pdfDownloadDomainService;

// After:
private final PdfDownloadPort pdfDownloadPort;
```

Call sites updated from `pdfDownloadDomainService.downloadPdf(url)` → `pdfDownloadPort.downloadPdf(url)`.

### Port Split from `domain/port/outbound/`

| Port | Target | Reason |
|---|---|---|
| `DocumentRepositoryPort` | `domain/repository/` | Domain-owned — repository is a domain concept |
| `MessagePublisherPort` | `application/port/out/` | Application concern — no domain logic |
| `OutboxRepositoryPort` | `application/port/out/` | Infrastructure concern surfaced as port |
| `StorageProviderPort` | `application/port/out/` | Application concern — storage is not domain logic |

### `OutboxReconciliationService` → `infrastructure/adapter/in/scheduler/`

`@Scheduled` services are inbound adapters (the scheduler drives application logic). `OutboxReconciliationService` moves from `infrastructure/messaging/` to `infrastructure/adapter/in/scheduler/`. Package declaration update only; `@Scheduled` annotations and all logic unchanged.

### `SecurityConfig` + `JwtConfigValidator` Extraction

Both are Spring `@Configuration`/`@Component` bean wiring classes nested inside `infrastructure/adapter/inbound/security/config/`. They belong in `infrastructure/config/security/`. The security adapter classes (`JwtService`, `JwtAuthenticationAdapter`, etc.) remain in `infrastructure/adapter/in/security/`.

### Config Sub-Package Split

| Class(es) | Sub-package | Rationale |
|---|---|---|
| `MetricsConfig` + `DocumentStorageMetricsService` | `infrastructure/config/metrics/` | Micrometer bean wiring + metrics |
| `OutboxConfig` | `infrastructure/config/outbox/` | Outbox CDC wiring |
| `ResilienceConfig` | `infrastructure/config/resilience/` | Resilience4j circuit breakers + retry |
| `SecurityConfig` + `JwtConfigValidator` | `infrastructure/config/security/` | Spring Security filter chain + JWT config |

---

## Dependency Rules

| Package | May import from | Must NOT import from |
|---|---|---|
| `domain/` | stdlib, Lombok, saga-commons | application/, infrastructure/ |
| `domain/repository/` | `domain/model/` | application/, infrastructure/ |
| `application/usecase/` | `domain/`, `application/port/out/`, `application/dto/` | infrastructure/ |
| `application/port/out/` | `domain/model/` | infrastructure/ |
| `application/dto/event/` | stdlib, Jackson, saga-commons | domain/, infrastructure/ |
| `infrastructure/adapter/in/` | `application/usecase/`, `application/dto/` | `infrastructure/adapter/out/` directly |
| `infrastructure/adapter/out/` | `application/port/out/`, `domain/`, `application/dto/` | `infrastructure/adapter/in/` |
| `infrastructure/config/` | everything (Spring wiring — allowed) | — |

---

## Data Flow

### Inbound: Saga Command (Kafka)
```
saga.command.document-storage / pdf-storage / signedxml-storage
  → infrastructure/adapter/in/messaging/SagaCommandAdapter (Camel)
  → SagaCommandUseCase (application/usecase/)
  → application/usecase/SagaOrchestrationService
      ├── application/port/out/PdfDownloadPort → infrastructure/adapter/out/http/PdfDownloadAdapter
      ├── application/usecase/FileStorageDomainService
      │     ├── application/port/out/StorageProviderPort → Local/S3FileStorageAdapter
      │     └── domain/repository/DocumentRepositoryPort → MongoDocumentAdapter
      └── application/port/out/MessagePublisherPort → MessagePublisherAdapter
            ↓ outbox → Debezium CDC → saga.reply.* / document.stored
```

### Inbound: REST Upload
```
POST /api/v1/documents
  → infrastructure/adapter/in/rest/DocumentStorageController
  → DocumentStorageUseCase → FileStorageDomainService
      ├── application/port/out/StorageProviderPort → Local/S3FileStorageAdapter
      └── domain/repository/DocumentRepositoryPort → MongoDocumentAdapter
```

### Inbound: Scheduled Reconciliation
```
@Scheduled → infrastructure/adapter/in/scheduler/OutboxReconciliationService
  → application/port/out/OutboxRepositoryPort → JpaOutboxEventRepository
  → application/port/out/MessagePublisherPort → MessagePublisherAdapter
```

---

## Import Mapping (Old → New)

| Old import | New import |
|---|---|
| `domain.event.*` | `application.dto.event.*` |
| `domain.port.inbound.*` | `application.usecase.*` |
| `domain.port.outbound.DocumentRepositoryPort` | `domain.repository.DocumentRepositoryPort` |
| `domain.port.outbound.MessagePublisherPort` | `application.port.out.MessagePublisherPort` |
| `domain.port.outbound.OutboxRepositoryPort` | `application.port.out.OutboxRepositoryPort` |
| `domain.port.outbound.StorageProviderPort` | `application.port.out.StorageProviderPort` |
| `domain.service.FileStorageDomainService` | `application.usecase.FileStorageDomainService` |
| `domain.service.SagaOrchestrationService` | `application.usecase.SagaOrchestrationService` |
| `domain.service.PdfDownloadDomainService` | `application.port.out.PdfDownloadPort` (as interface) |
| `infrastructure.adapter.inbound.*` | `infrastructure.adapter.in.*` |
| `infrastructure.adapter.outbound.*` | `infrastructure.adapter.out.*` |
| `infrastructure.adapter.inbound.security.config.SecurityConfig` | `infrastructure.config.security.SecurityConfig` |
| `infrastructure.adapter.inbound.security.config.JwtConfigValidator` | `infrastructure.config.security.JwtConfigValidator` |
| `infrastructure.config.MetricsConfig` | `infrastructure.config.metrics.MetricsConfig` |
| `infrastructure.config.DocumentStorageMetricsService` | `infrastructure.config.metrics.DocumentStorageMetricsService` |
| `infrastructure.config.OutboxConfig` | `infrastructure.config.outbox.OutboxConfig` |
| `infrastructure.config.ResilienceConfig` | `infrastructure.config.resilience.ResilienceConfig` |
| `infrastructure.messaging.OutboxReconciliationService` | `infrastructure.adapter.in.scheduler.OutboxReconciliationService` |

---

## Migration Phases

| Phase | Scope | Commit message |
|---|---|---|
| 1 | Move `domain/event/` → `application/dto/event/`; split `domain/port/outbound/` → `domain/repository/` + `application/port/out/`; create `PdfDownloadPort` | `Move Kafka DTOs to application/dto/event, split domain/port/outbound, add PdfDownloadPort` |
| 2 | Dissolve `domain/port/inbound/` + `domain/service/` → `application/usecase/`; move `PdfDownloadDomainService` → `infrastructure/adapter/out/http/PdfDownloadAdapter` | `Merge domain/port/inbound and domain/service into application/usecase, extract PdfDownloadAdapter` |
| 3 | Rename `adapter/inbound/` → `adapter/in/`, `adapter/outbound/` → `adapter/out/`; move `OutboxReconciliationService` → `adapter/in/scheduler/`; extract `SecurityConfig`+`JwtConfigValidator` → `infrastructure/config/security/` | `Rename inbound→in, outbound→out, move scheduler and security config` |
| 4 | Split `infrastructure/config/` → concern sub-packages | `Move infrastructure/config to concern-based sub-packages` |
| 5 | Relocate test files, update JaCoCo exclusions | `Relocate test classes, update JaCoCo exclusions` |
| 6 | Final verification — `mvn verify`, confirm no old package references | (verification only) |

---

## Testing Strategy

### Test Relocations (Phase 5)

| Old test path | New test path |
|---|---|
| `domain/event/*Test` (8 files) | `application/dto/event/` |
| `domain/service/FileStorageDomainServiceTest` | `application/usecase/` |
| `domain/service/PdfDownloadDomainServiceTest` | `infrastructure/adapter/out/http/` (rename to `PdfDownloadAdapterTest`) |
| `domain/service/SagaOrchestrationServiceTest` | `application/usecase/` |
| `infrastructure/adapter/inbound/messaging/SagaCommandAdapterTest` | `infrastructure/adapter/in/messaging/` |
| `infrastructure/adapter/inbound/rest/*Test` (4 files) | `infrastructure/adapter/in/rest/` |
| `infrastructure/adapter/security/*Test` (4 files) | `infrastructure/adapter/in/security/` |
| `infrastructure/adapter/security/config/*Test` (2 files) | `infrastructure/config/security/` |
| `infrastructure/adapter/security/exception/SecurityExceptionTest` | `infrastructure/adapter/in/security/exception/` |
| `infrastructure/adapter/outbound/messaging/MessagePublisherAdapterTest` | `infrastructure/adapter/out/messaging/` |
| `infrastructure/adapter/outbound/persistence/*Test` (3 files) | `infrastructure/adapter/out/persistence/` |
| `infrastructure/adapter/outbound/persistence/outbox/*Test` (2 files) | `infrastructure/adapter/out/persistence/outbox/` |
| `infrastructure/adapter/outbound/storage/*Test` (2 files) | `infrastructure/adapter/out/storage/` |
| `infrastructure/config/OutboxConfigTest` | `infrastructure/config/outbox/` |

**Not moved:** `domain/model/*Test`, `domain/exception/*Test`, `domain/util/ContentTypeUtilTest`, `chaos/`, `integration/`.

### New Test Required

`PdfDownloadDomainServiceTest` is renamed to `PdfDownloadAdapterTest` and moved to `infrastructure/adapter/out/http/`. Update:
- Package declaration
- Class name reference (`PdfDownloadDomainService` → `PdfDownloadAdapter`)
- Add interface contract assertion: `assertThat(adapter).isInstanceOf(PdfDownloadPort.class)`

### JaCoCo Updates

Update any exclusion patterns in `pom.xml` that reference old paths (see import mapping table above for path changes).

### Coverage Target

Maintain ≥ 40% instruction / 25% branch / 50% class coverage (`mvn verify`) throughout all phases.

---

## Key Decisions

| Decision | Rationale |
|---|---|
| `PdfDownloadDomainService` → `infrastructure/adapter/out/http/PdfDownloadAdapter` + `PdfDownloadPort` | HTTP clients are infrastructure; domain and application layers must not make direct HTTP calls |
| `domain/event/` fully removed | All 10 files are Kafka wire DTOs, not domain events; domain has no Kafka knowledge |
| `domain/port/` fully removed | Ports split to canonical locations: `domain/repository/` and `application/port/out/` |
| `domain/service/` fully removed | Use-case implementations co-locate with use-case interfaces in `application/usecase/` |
| `OutboxReconciliationService` → `adapter/in/scheduler/` | `@Scheduled` services are inbound adapters (scheduler drives the application) |
| `SecurityConfig` extracted to `infrastructure/config/security/` | Spring `@Configuration` beans belong in config layer, not nested inside adapter sub-packages |
| `inbound/outbound` → `in/out` | Canonical naming used by all other services in the pipeline |
