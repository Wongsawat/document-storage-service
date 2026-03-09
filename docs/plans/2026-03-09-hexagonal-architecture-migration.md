# Document-Storage-Service Hexagonal Architecture Canonical Alignment

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Align document-storage-service with the canonical hexagonal layout — move Kafka wire DTOs out of `domain/`, dissolve `domain/port/` and `domain/service/` into `application/`, rename `inbound/outbound` adapters to `in/out`, and extract `PdfDownloadDomainService` as a proper `infrastructure/adapter/out/http/` adapter.

**Architecture:** Pure package rename + relocation with one new interface (`PdfDownloadPort`). `domain/` ← `application/` ← `infrastructure/` strict dependency rule. `domain/event/` fully dissolved into `application/dto/event/`. `domain/port/` split: repository interface to `domain/repository/`, others to `application/port/out/`. `domain/service/` use-case implementations merge into `application/usecase/`. HTTP download extracted from domain to infrastructure.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Apache Camel 4.x, MongoDB, PostgreSQL, Maven, JUnit 5, JaCoCo 0.8.11

**Design doc:** `docs/plans/2026-03-09-hexagonal-architecture-migration-design.md`

---

## Pre-flight

Confirm all unit tests pass:

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/document-storage-service
mvn test -q
```

Expected: BUILD SUCCESS. If not, stop and fix first.

---

## Phase 1 — Dissolve `domain/event/` and split `domain/port/outbound/`

### Task 1: Move Kafka event DTOs to `application/dto/event/`

All 10 classes in `domain/event/` are Kafka wire DTOs (saga commands, reply events). They are not domain events. Move them to `application/dto/event/`.

**Files:**
- Move all 10 files from `src/main/java/com/wpanther/storage/domain/event/`
  to `src/main/java/com/wpanther/storage/application/dto/event/`

**Step 1: Create target directory**

```bash
mkdir -p src/main/java/com/wpanther/storage/application/dto/event
```

**Step 2: Move all event files — update package declarations**

For every file in `domain/event/`:
```java
// Before:
package com.wpanther.storage.domain.event;

// After:
package com.wpanther.storage.application.dto.event;
```

```bash
for f in \
  CompensateDocumentStorageCommand \
  CompensatePdfStorageCommand \
  CompensateSignedXmlStorageCommand \
  DocumentStorageReplyEvent \
  DocumentStoredEvent \
  PdfStorageReplyEvent \
  ProcessDocumentStorageCommand \
  ProcessPdfStorageCommand \
  ProcessSignedXmlStorageCommand \
  SignedXmlStorageReplyEvent; do
  mv "src/main/java/com/wpanther/storage/domain/event/${f}.java" \
     "src/main/java/com/wpanther/storage/application/dto/event/${f}.java"
done
rmdir src/main/java/com/wpanther/storage/domain/event
```

**Step 3: Find all importers of `domain.event.*`**

```bash
grep -rl "storage\.domain\.event" src/main/
```

Update every found file:
```java
// Before:
import com.wpanther.storage.domain.event.ProcessDocumentStorageCommand;
// (all other domain.event imports)

// After:
import com.wpanther.storage.application.dto.event.ProcessDocumentStorageCommand;
// (all other application.dto.event imports)
```

**Step 4: Compile**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS. Fix any "cannot find symbol" before continuing.

**Step 5: Run tests**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 6: Commit**

```bash
git add src/main/java/com/wpanther/storage/application/dto/
git rm -r src/main/java/com/wpanther/storage/domain/event/ 2>/dev/null || true
git add -u
git commit -m "Move Kafka event DTOs from domain/event to application/dto/event"
```

---

### Task 2: Split `domain/port/outbound/` — move to `domain/repository/` and `application/port/out/`

`DocumentRepositoryPort` is domain-owned and moves to `domain/repository/`. The other three ports (`MessagePublisherPort`, `OutboxRepositoryPort`, `StorageProviderPort`) are application concerns and move to `application/port/out/`.

**Files:**
- Move: `domain/port/outbound/DocumentRepositoryPort.java` → `domain/repository/DocumentRepositoryPort.java`
- Move: `domain/port/outbound/MessagePublisherPort.java` → `application/port/out/MessagePublisherPort.java`
- Move: `domain/port/outbound/OutboxRepositoryPort.java` → `application/port/out/OutboxRepositoryPort.java`
- Move: `domain/port/outbound/StorageProviderPort.java` → `application/port/out/StorageProviderPort.java`

**Step 1: Create target directories**

```bash
mkdir -p src/main/java/com/wpanther/storage/domain/repository
mkdir -p src/main/java/com/wpanther/storage/application/port/out
```

**Step 2: Move `DocumentRepositoryPort.java` — update package declaration**

```java
// Before:
package com.wpanther.storage.domain.port.outbound;

// After:
package com.wpanther.storage.domain.repository;
```

```bash
mv src/main/java/com/wpanther/storage/domain/port/outbound/DocumentRepositoryPort.java \
   src/main/java/com/wpanther/storage/domain/repository/DocumentRepositoryPort.java
```

**Step 3: Move the three application-layer ports — update package declarations**

For `MessagePublisherPort`, `OutboxRepositoryPort`, `StorageProviderPort`:
```java
// Before:
package com.wpanther.storage.domain.port.outbound;

// After:
package com.wpanther.storage.application.port.out;
```

```bash
for f in MessagePublisherPort OutboxRepositoryPort StorageProviderPort; do
  mv "src/main/java/com/wpanther/storage/domain/port/outbound/${f}.java" \
     "src/main/java/com/wpanther/storage/application/port/out/${f}.java"
done
rmdir src/main/java/com/wpanther/storage/domain/port/outbound
```

**Step 4: Create `PdfDownloadPort.java` in `application/port/out/`**

Inspect `PdfDownloadDomainService.java` to identify the exact download method signature. Then create:

```java
package com.wpanther.storage.application.port.out;

/**
 * Outbound port for downloading PDF files from external URLs.
 * Implemented by infrastructure/adapter/out/http/PdfDownloadAdapter.
 */
public interface PdfDownloadPort {

    /**
     * Downloads a PDF from the given URL and returns its bytes.
     *
     * @param url the URL to download from (e.g. MinIO presigned URL)
     * @return the downloaded PDF bytes
     */
    byte[] downloadPdf(String url);
}
```

> **Note:** Check `PdfDownloadDomainService.java` for the exact method name and signature. If the method is named differently (e.g. `download(String url)` or returns a different type), match it exactly in the interface.

**Step 5: Find all importers of old outbound paths**

```bash
grep -rl "storage\.domain\.port\.outbound" src/main/
```

Update each found file:
```java
// Before:
import com.wpanther.storage.domain.port.outbound.DocumentRepositoryPort;
import com.wpanther.storage.domain.port.outbound.MessagePublisherPort;
import com.wpanther.storage.domain.port.outbound.OutboxRepositoryPort;
import com.wpanther.storage.domain.port.outbound.StorageProviderPort;

// After:
import com.wpanther.storage.domain.repository.DocumentRepositoryPort;
import com.wpanther.storage.application.port.out.MessagePublisherPort;
import com.wpanther.storage.application.port.out.OutboxRepositoryPort;
import com.wpanther.storage.application.port.out.StorageProviderPort;
```

**Step 6: Compile and test**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 7: Commit**

```bash
git add src/main/java/com/wpanther/storage/domain/repository/ \
        src/main/java/com/wpanther/storage/application/port/out/
git rm -r src/main/java/com/wpanther/storage/domain/port/outbound/ 2>/dev/null || true
git add -u
git commit -m "Split domain/port/outbound: DocumentRepositoryPort to domain/repository, others to application/port/out, add PdfDownloadPort"
```

---

## Phase 2 — Dissolve `domain/port/inbound/` and `domain/service/`

### Task 3: Merge use-case interfaces and implementations into `application/usecase/`

`domain/port/inbound/` (3 interfaces) and `domain/service/` (2 use-case implementations) all move to `application/usecase/`. `PdfDownloadDomainService` is extracted separately as an infrastructure adapter (Task 4).

**Files:**
- Move: `domain/port/inbound/AuthenticationUseCase.java` → `application/usecase/`
- Move: `domain/port/inbound/DocumentStorageUseCase.java` → `application/usecase/`
- Move: `domain/port/inbound/SagaCommandUseCase.java` → `application/usecase/`
- Move: `domain/service/FileStorageDomainService.java` → `application/usecase/`
- Move: `domain/service/SagaOrchestrationService.java` → `application/usecase/`

**Step 1: Create target directory**

```bash
mkdir -p src/main/java/com/wpanther/storage/application/usecase
```

**Step 2: Move the three inbound port interfaces — update package declarations**

For `AuthenticationUseCase`, `DocumentStorageUseCase`, `SagaCommandUseCase`:
```java
// Before:
package com.wpanther.storage.domain.port.inbound;

// After:
package com.wpanther.storage.application.usecase;
```

```bash
for f in AuthenticationUseCase DocumentStorageUseCase SagaCommandUseCase; do
  mv "src/main/java/com/wpanther/storage/domain/port/inbound/${f}.java" \
     "src/main/java/com/wpanther/storage/application/usecase/${f}.java"
done
rmdir src/main/java/com/wpanther/storage/domain/port/inbound
rmdir src/main/java/com/wpanther/storage/domain/port 2>/dev/null || true
```

**Step 3: Move `FileStorageDomainService.java` — update package declaration and imports**

```java
// Before:
package com.wpanther.storage.domain.service;
// imports referencing domain.port.inbound and domain.port.outbound

// After:
package com.wpanther.storage.application.usecase;
// imports updated to application.usecase.* and domain.repository.* and application.port.out.*
```

```bash
mv src/main/java/com/wpanther/storage/domain/service/FileStorageDomainService.java \
   src/main/java/com/wpanther/storage/application/usecase/FileStorageDomainService.java
```

Update imports in the moved file:
```java
// Before:
import com.wpanther.storage.domain.port.inbound.DocumentStorageUseCase;
import com.wpanther.storage.domain.port.outbound.DocumentRepositoryPort;
import com.wpanther.storage.domain.port.outbound.StorageProviderPort;

// After:
import com.wpanther.storage.application.usecase.DocumentStorageUseCase;  // same package — can remove
import com.wpanther.storage.domain.repository.DocumentRepositoryPort;
import com.wpanther.storage.application.port.out.StorageProviderPort;
```

> **Tip:** `DocumentStorageUseCase` is now in the same package as `FileStorageDomainService` — the explicit import can be removed.

**Step 4: Move `SagaOrchestrationService.java` — update package declaration, imports, and field type**

```java
// Before:
package com.wpanther.storage.domain.service;
// ...
import com.wpanther.storage.domain.service.PdfDownloadDomainService;
// ...
private final PdfDownloadDomainService pdfDownloadDomainService;

// After:
package com.wpanther.storage.application.usecase;
// ...
import com.wpanther.storage.application.port.out.PdfDownloadPort;
// ...
private final PdfDownloadPort pdfDownloadPort;
```

Also update:
- All `pdfDownloadDomainService.*` call sites → `pdfDownloadPort.*` (same method names)
- All `domain.port.inbound.*` imports → same-package (remove or keep `application.usecase.*`)
- All `domain.port.outbound.*` imports → `domain.repository.*` or `application.port.out.*`
- All `domain.event.*` imports → `application.dto.event.*`

```bash
mv src/main/java/com/wpanther/storage/domain/service/SagaOrchestrationService.java \
   src/main/java/com/wpanther/storage/application/usecase/SagaOrchestrationService.java
```

**Step 5: Find and update all importers of old inbound/service paths**

```bash
grep -rl "storage\.domain\.port\.inbound\|storage\.domain\.service\." src/main/
```

Update each found file:
```java
// Before:
import com.wpanther.storage.domain.port.inbound.SagaCommandUseCase;
import com.wpanther.storage.domain.port.inbound.DocumentStorageUseCase;
import com.wpanther.storage.domain.port.inbound.AuthenticationUseCase;
import com.wpanther.storage.domain.service.FileStorageDomainService;
import com.wpanther.storage.domain.service.SagaOrchestrationService;

// After:
import com.wpanther.storage.application.usecase.SagaCommandUseCase;
import com.wpanther.storage.application.usecase.DocumentStorageUseCase;
import com.wpanther.storage.application.usecase.AuthenticationUseCase;
import com.wpanther.storage.application.usecase.FileStorageDomainService;
import com.wpanther.storage.application.usecase.SagaOrchestrationService;
```

**Step 6: Compile**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS. If not, check for any remaining `domain.port.inbound` or `domain.service` imports:
```bash
grep -r "storage\.domain\.port\.inbound\|storage\.domain\.service\." src/main/
```

**Step 7: Run tests**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 8: Commit**

```bash
git add src/main/java/com/wpanther/storage/application/usecase/
git rm -r src/main/java/com/wpanther/storage/domain/port/inbound/ 2>/dev/null || true
git rm src/main/java/com/wpanther/storage/domain/service/FileStorageDomainService.java 2>/dev/null || true
git rm src/main/java/com/wpanther/storage/domain/service/SagaOrchestrationService.java 2>/dev/null || true
git add -u
git commit -m "Merge domain/port/inbound and domain/service into application/usecase"
```

---

### Task 4: Extract `PdfDownloadDomainService` as `infrastructure/adapter/out/http/PdfDownloadAdapter`

`PdfDownloadDomainService` makes HTTP calls — it is an infrastructure adapter, not a domain service. Rename it to `PdfDownloadAdapter` and move it to `infrastructure/adapter/out/http/`. Add `implements PdfDownloadPort`.

**Files:**
- Create: `src/main/java/com/wpanther/storage/infrastructure/adapter/out/http/PdfDownloadAdapter.java`
- Delete: `src/main/java/com/wpanther/storage/domain/service/PdfDownloadDomainService.java`

**Step 1: Create target directory**

```bash
mkdir -p src/main/java/com/wpanther/storage/infrastructure/adapter/out/http
```

**Step 2: Copy content and update**

Open `src/main/java/com/wpanther/storage/domain/service/PdfDownloadDomainService.java`.

Create `src/main/java/com/wpanther/storage/infrastructure/adapter/out/http/PdfDownloadAdapter.java` with:
- Package changed from `domain.service` → `infrastructure.adapter.out.http`
- Class name changed from `PdfDownloadDomainService` → `PdfDownloadAdapter`
- `implements PdfDownloadPort` added to the class declaration
- Import added: `import com.wpanther.storage.application.port.out.PdfDownloadPort;`
- All method bodies **unchanged**
- All other imports remain the same

Example class declaration change:
```java
// Before:
public class PdfDownloadDomainService {

// After:
public class PdfDownloadAdapter implements PdfDownloadPort {
```

**Step 3: Delete the old file**

```bash
git rm src/main/java/com/wpanther/storage/domain/service/PdfDownloadDomainService.java
rmdir src/main/java/com/wpanther/storage/domain/service 2>/dev/null || true
```

**Step 4: Verify `SagaOrchestrationService` injects `PdfDownloadPort`**

`SagaOrchestrationService` (moved in Task 3) should already have `PdfDownloadPort` as the field type. Verify:
```bash
grep "PdfDownload" src/main/java/com/wpanther/storage/application/usecase/SagaOrchestrationService.java
```

Expected: `PdfDownloadPort` (not `PdfDownloadDomainService`).

**Step 5: Compile and test**

```bash
mvn test -q
```

Expected: BUILD SUCCESS. Spring's `@ComponentScan` will discover `PdfDownloadAdapter` in the `infrastructure.adapter.out.http` package automatically.

**Step 6: Commit**

```bash
git add src/main/java/com/wpanther/storage/infrastructure/adapter/out/http/PdfDownloadAdapter.java
git add -u
git commit -m "Extract PdfDownloadDomainService as PdfDownloadAdapter in infrastructure/adapter/out/http"
```

---

## Phase 3 — Rename Adapters and Relocate Infrastructure

### Task 5: Rename `adapter/inbound/` → `adapter/in/` and `adapter/outbound/` → `adapter/out/`

The largest move of the migration. All files in the adapter layer get new package paths. Logic unchanged.

**Files affected:**
- All files under `infrastructure/adapter/inbound/` → `infrastructure/adapter/in/`
- All files under `infrastructure/adapter/outbound/` → `infrastructure/adapter/out/`

**Step 1: Create all target directories**

```bash
mkdir -p src/main/java/com/wpanther/storage/infrastructure/adapter/in/messaging
mkdir -p src/main/java/com/wpanther/storage/infrastructure/adapter/in/rest/config
mkdir -p src/main/java/com/wpanther/storage/infrastructure/adapter/in/security/config
mkdir -p src/main/java/com/wpanther/storage/infrastructure/adapter/in/security/exception
mkdir -p src/main/java/com/wpanther/storage/infrastructure/adapter/out/messaging
mkdir -p src/main/java/com/wpanther/storage/infrastructure/adapter/out/persistence/outbox
mkdir -p src/main/java/com/wpanther/storage/infrastructure/adapter/out/storage
```

(Note: `infrastructure/adapter/out/http/` already created in Task 4.)

**Step 2: Move `adapter/inbound/messaging/`**

```bash
mv src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/messaging/SagaCommandAdapter.java \
   src/main/java/com/wpanther/storage/infrastructure/adapter/in/messaging/SagaCommandAdapter.java
rmdir src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/messaging
```

Update package declaration:
```java
// Before:
package com.wpanther.storage.infrastructure.adapter.inbound.messaging;
// After:
package com.wpanther.storage.infrastructure.adapter.in.messaging;
```

**Step 3: Move `adapter/inbound/rest/`**

```bash
for f in AuthenticationController DocumentStorageController DocumentValidator; do
  mv "src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/rest/${f}.java" \
     "src/main/java/com/wpanther/storage/infrastructure/adapter/in/rest/${f}.java"
done
mv src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/rest/config/ApiVersion.java \
   src/main/java/com/wpanther/storage/infrastructure/adapter/in/rest/config/ApiVersion.java
rmdir src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/rest/config
rmdir src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/rest
```

Update package declarations:
```java
// Before:
package com.wpanther.storage.infrastructure.adapter.inbound.rest;
// After:
package com.wpanther.storage.infrastructure.adapter.in.rest;

// Before:
package com.wpanther.storage.infrastructure.adapter.inbound.rest.config;
// After:
package com.wpanther.storage.infrastructure.adapter.in.rest.config;
```

**Step 4: Move `adapter/inbound/security/` (excluding `config/` sub-package)**

```bash
for f in \
  DocumentStorageUserDetailsService \
  JwtAccessDeniedHandler \
  JwtAuthenticationAdapter \
  JwtAuthenticationEntryPoint \
  JwtService \
  RateLimitingFilter \
  TokenBlacklistService; do
  mv "src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/security/${f}.java" \
     "src/main/java/com/wpanther/storage/infrastructure/adapter/in/security/${f}.java"
done

# Move exception classes
for f in AuthenticationFailedException AuthorizationFailedException InvalidTokenException SecurityException; do
  mv "src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/security/exception/${f}.java" \
     "src/main/java/com/wpanther/storage/infrastructure/adapter/in/security/exception/${f}.java"
done
rmdir src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/security/exception
```

Update package declarations for security classes:
```java
// Before:
package com.wpanther.storage.infrastructure.adapter.inbound.security;
// After:
package com.wpanther.storage.infrastructure.adapter.in.security;

// Before:
package com.wpanther.storage.infrastructure.adapter.inbound.security.exception;
// After:
package com.wpanther.storage.infrastructure.adapter.in.security.exception;
```

**Step 5: Move `SecurityConfig` and `JwtConfigValidator` to `infrastructure/config/security/`**

These are `@Configuration` classes — they belong in `infrastructure/config/`, not inside the adapter tree.

```bash
mkdir -p src/main/java/com/wpanther/storage/infrastructure/config/security

for f in SecurityConfig JwtConfigValidator; do
  mv "src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/security/config/${f}.java" \
     "src/main/java/com/wpanther/storage/infrastructure/config/security/${f}.java"
done
rmdir src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/security/config
rmdir src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/security
rmdir src/main/java/com/wpanther/storage/infrastructure/adapter/inbound
```

Update package declarations:
```java
// Before:
package com.wpanther.storage.infrastructure.adapter.inbound.security.config;
// After:
package com.wpanther.storage.infrastructure.config.security;
```

**Step 6: Move `adapter/outbound/messaging/`**

```bash
mv src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/messaging/MessagePublisherAdapter.java \
   src/main/java/com/wpanther/storage/infrastructure/adapter/out/messaging/MessagePublisherAdapter.java
rmdir src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/messaging
```

Update package declaration:
```java
// Before:
package com.wpanther.storage.infrastructure.adapter.outbound.messaging;
// After:
package com.wpanther.storage.infrastructure.adapter.out.messaging;
```

**Step 7: Move `adapter/outbound/persistence/`**

```bash
for f in DocumentRepositoryAdapter MongoDocumentAdapter MongoOutboxEventAdapter StoredDocumentEntity StoredDocumentMapper; do
  mv "src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/persistence/${f}.java" \
     "src/main/java/com/wpanther/storage/infrastructure/adapter/out/persistence/${f}.java"
done

for f in JpaOutboxEventRepository OutboxEventEntity SpringDataOutboxRepository; do
  mv "src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/persistence/outbox/${f}.java" \
     "src/main/java/com/wpanther/storage/infrastructure/adapter/out/persistence/outbox/${f}.java"
done

rmdir src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/persistence/outbox
rmdir src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/persistence
```

Update package declarations:
```java
// Before:
package com.wpanther.storage.infrastructure.adapter.outbound.persistence;
// After:
package com.wpanther.storage.infrastructure.adapter.out.persistence;

// Before:
package com.wpanther.storage.infrastructure.adapter.outbound.persistence.outbox;
// After:
package com.wpanther.storage.infrastructure.adapter.out.persistence.outbox;
```

**Step 8: Move `adapter/outbound/storage/`**

```bash
for f in LocalFileStorageAdapter S3FileStorageAdapter; do
  mv "src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/storage/${f}.java" \
     "src/main/java/com/wpanther/storage/infrastructure/adapter/out/storage/${f}.java"
done
rmdir src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/storage
rmdir src/main/java/com/wpanther/storage/infrastructure/adapter/outbound
```

Update package declarations:
```java
// Before:
package com.wpanther.storage.infrastructure.adapter.outbound.storage;
// After:
package com.wpanther.storage.infrastructure.adapter.out.storage;
```

**Step 9: Move `OutboxReconciliationService` to `infrastructure/adapter/in/scheduler/`**

```bash
mkdir -p src/main/java/com/wpanther/storage/infrastructure/adapter/in/scheduler

mv src/main/java/com/wpanther/storage/infrastructure/messaging/OutboxReconciliationService.java \
   src/main/java/com/wpanther/storage/infrastructure/adapter/in/scheduler/OutboxReconciliationService.java

rmdir src/main/java/com/wpanther/storage/infrastructure/messaging
```

Update package declaration:
```java
// Before:
package com.wpanther.storage.infrastructure.messaging;
// After:
package com.wpanther.storage.infrastructure.adapter.in.scheduler;
```

**Step 10: Find and update ALL cross-package importers**

```bash
grep -rl "infrastructure\.adapter\.inbound\.\|infrastructure\.adapter\.outbound\.\|infrastructure\.messaging\." src/main/
```

Apply the full import mapping for each found file:

| Old import pattern | New import pattern |
|---|---|
| `infrastructure.adapter.inbound.messaging.*` | `infrastructure.adapter.in.messaging.*` |
| `infrastructure.adapter.inbound.rest.*` | `infrastructure.adapter.in.rest.*` |
| `infrastructure.adapter.inbound.security.*` | `infrastructure.adapter.in.security.*` |
| `infrastructure.adapter.inbound.security.config.*` | `infrastructure.config.security.*` |
| `infrastructure.adapter.outbound.messaging.*` | `infrastructure.adapter.out.messaging.*` |
| `infrastructure.adapter.outbound.persistence.*` | `infrastructure.adapter.out.persistence.*` |
| `infrastructure.adapter.outbound.storage.*` | `infrastructure.adapter.out.storage.*` |
| `infrastructure.messaging.OutboxReconciliationService` | `infrastructure.adapter.in.scheduler.OutboxReconciliationService` |

**Step 11: Compile**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS. If not, run:
```bash
grep -r "\.adapter\.inbound\.\|\.adapter\.outbound\.\|infrastructure\.messaging\." src/main/ | grep "\.java:"
```

Fix all remaining old references.

**Step 12: Run tests**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 13: Verify `adapter/inbound/` and `adapter/outbound/` are gone**

```bash
find src/main/ -path "*/adapter/inbound*" -type f
find src/main/ -path "*/adapter/outbound*" -type f
find src/main/ -path "*/infrastructure/messaging*" -type f
```

Expected: no output from any command.

**Step 14: Commit**

```bash
git add src/main/java/com/wpanther/storage/infrastructure/adapter/in/ \
        src/main/java/com/wpanther/storage/infrastructure/adapter/out/ \
        src/main/java/com/wpanther/storage/infrastructure/config/security/
git rm -r src/main/java/com/wpanther/storage/infrastructure/adapter/inbound/ 2>/dev/null || true
git rm -r src/main/java/com/wpanther/storage/infrastructure/adapter/outbound/ 2>/dev/null || true
git rm -r src/main/java/com/wpanther/storage/infrastructure/messaging/ 2>/dev/null || true
git add -u
git commit -m "Rename inbound→in, outbound→out; move OutboxReconciliationService to scheduler; move SecurityConfig to config/security"
```

---

## Phase 4 — Split `infrastructure/config/` to Sub-Packages

### Task 6: Move config classes to concern-based sub-packages

**Files:**
- Move: `infrastructure/config/MetricsConfig.java` + `DocumentStorageMetricsService.java` → `infrastructure/config/metrics/`
- Move: `infrastructure/config/OutboxConfig.java` → `infrastructure/config/outbox/`
- Move: `infrastructure/config/ResilienceConfig.java` → `infrastructure/config/resilience/`

(`SecurityConfig` and `JwtConfigValidator` were already moved in Task 5.)

**Step 1: Create target directories**

```bash
mkdir -p src/main/java/com/wpanther/storage/infrastructure/config/metrics
mkdir -p src/main/java/com/wpanther/storage/infrastructure/config/outbox
mkdir -p src/main/java/com/wpanther/storage/infrastructure/config/resilience
```

**Step 2: Move `MetricsConfig.java` and `DocumentStorageMetricsService.java`**

```java
// Before:
package com.wpanther.storage.infrastructure.config;
// After:
package com.wpanther.storage.infrastructure.config.metrics;
```

```bash
mv src/main/java/com/wpanther/storage/infrastructure/config/MetricsConfig.java \
   src/main/java/com/wpanther/storage/infrastructure/config/metrics/MetricsConfig.java
mv src/main/java/com/wpanther/storage/infrastructure/config/DocumentStorageMetricsService.java \
   src/main/java/com/wpanther/storage/infrastructure/config/metrics/DocumentStorageMetricsService.java
```

**Step 3: Move `OutboxConfig.java`**

```java
// Before:
package com.wpanther.storage.infrastructure.config;
// After:
package com.wpanther.storage.infrastructure.config.outbox;
```

```bash
mv src/main/java/com/wpanther/storage/infrastructure/config/OutboxConfig.java \
   src/main/java/com/wpanther/storage/infrastructure/config/outbox/OutboxConfig.java
```

**Step 4: Move `ResilienceConfig.java`**

```java
// Before:
package com.wpanther.storage.infrastructure.config;
// After:
package com.wpanther.storage.infrastructure.config.resilience;
```

```bash
mv src/main/java/com/wpanther/storage/infrastructure/config/ResilienceConfig.java \
   src/main/java/com/wpanther/storage/infrastructure/config/resilience/ResilienceConfig.java
```

**Step 5: Find all importers of old flat config paths**

```bash
grep -rl "infrastructure\.config\.[A-Z]" src/main/
```

Update each found file:
```java
// Before:
import com.wpanther.storage.infrastructure.config.MetricsConfig;
import com.wpanther.storage.infrastructure.config.DocumentStorageMetricsService;
import com.wpanther.storage.infrastructure.config.OutboxConfig;
import com.wpanther.storage.infrastructure.config.ResilienceConfig;

// After:
import com.wpanther.storage.infrastructure.config.metrics.MetricsConfig;
import com.wpanther.storage.infrastructure.config.metrics.DocumentStorageMetricsService;
import com.wpanther.storage.infrastructure.config.outbox.OutboxConfig;
import com.wpanther.storage.infrastructure.config.resilience.ResilienceConfig;
```

**Step 6: Verify flat `infrastructure/config/` is empty of `.java` files**

```bash
find src/main/java/com/wpanther/storage/infrastructure/config -maxdepth 1 -name "*.java"
```

Expected: no output.

**Step 7: Compile and test**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 8: Commit**

```bash
git add src/main/java/com/wpanther/storage/infrastructure/config/metrics/ \
        src/main/java/com/wpanther/storage/infrastructure/config/outbox/ \
        src/main/java/com/wpanther/storage/infrastructure/config/resilience/
git rm src/main/java/com/wpanther/storage/infrastructure/config/MetricsConfig.java 2>/dev/null || true
git rm src/main/java/com/wpanther/storage/infrastructure/config/DocumentStorageMetricsService.java 2>/dev/null || true
git rm src/main/java/com/wpanther/storage/infrastructure/config/OutboxConfig.java 2>/dev/null || true
git rm src/main/java/com/wpanther/storage/infrastructure/config/ResilienceConfig.java 2>/dev/null || true
git add -u
git commit -m "Move infrastructure/config to concern-based sub-packages (metrics, outbox, resilience)"
```

---

## Phase 5 — Relocate Test Files and Update JaCoCo

### Task 7: Relocate test files to mirror new production structure

**Step 1: Create target test directories**

```bash
mkdir -p src/test/java/com/wpanther/storage/application/dto/event
mkdir -p src/test/java/com/wpanther/storage/application/usecase
mkdir -p src/test/java/com/wpanther/storage/infrastructure/adapter/in/messaging
mkdir -p src/test/java/com/wpanther/storage/infrastructure/adapter/in/rest
mkdir -p src/test/java/com/wpanther/storage/infrastructure/adapter/in/security/config
mkdir -p src/test/java/com/wpanther/storage/infrastructure/adapter/in/security/exception
mkdir -p src/test/java/com/wpanther/storage/infrastructure/adapter/out/messaging
mkdir -p src/test/java/com/wpanther/storage/infrastructure/adapter/out/persistence/outbox
mkdir -p src/test/java/com/wpanther/storage/infrastructure/adapter/out/storage
mkdir -p src/test/java/com/wpanther/storage/infrastructure/adapter/out/http
mkdir -p src/test/java/com/wpanther/storage/infrastructure/config/outbox
mkdir -p src/test/java/com/wpanther/storage/infrastructure/config/security
```

**Step 2: Move domain/event test files**

```bash
for f in \
  CompensateDocumentStorageCommandTest \
  CompensatePdfStorageCommandTest \
  CompensateSignedXmlStorageCommandTest \
  DocumentStorageReplyEventTest \
  DocumentStoredEventTest \
  ProcessDocumentStorageCommandTest \
  ProcessSignedXmlStorageCommandTest \
  SignedXmlStorageReplyEventTest; do
  mv "src/test/java/com/wpanther/storage/domain/event/${f}.java" \
     "src/test/java/com/wpanther/storage/application/dto/event/${f}.java"
done
rmdir src/test/java/com/wpanther/storage/domain/event
```

Update package declaration in each file:
```java
// Before:
package com.wpanther.storage.domain.event;
// After:
package com.wpanther.storage.application.dto.event;
```

**Step 3: Move domain/service test files**

```bash
mv src/test/java/com/wpanther/storage/domain/service/FileStorageDomainServiceTest.java \
   src/test/java/com/wpanther/storage/application/usecase/FileStorageDomainServiceTest.java
mv src/test/java/com/wpanther/storage/domain/service/SagaOrchestrationServiceTest.java \
   src/test/java/com/wpanther/storage/application/usecase/SagaOrchestrationServiceTest.java
mv src/test/java/com/wpanther/storage/domain/service/PdfDownloadDomainServiceTest.java \
   src/test/java/com/wpanther/storage/infrastructure/adapter/out/http/PdfDownloadAdapterTest.java
rmdir src/test/java/com/wpanther/storage/domain/service
```

Update package declarations:
```java
// FileStorageDomainServiceTest + SagaOrchestrationServiceTest:
// Before: package com.wpanther.storage.domain.service;
// After:  package com.wpanther.storage.application.usecase;

// PdfDownloadAdapterTest (renamed from PdfDownloadDomainServiceTest):
// Before: package com.wpanther.storage.domain.service;
// After:  package com.wpanther.storage.infrastructure.adapter.out.http;
```

In `PdfDownloadAdapterTest.java`:
- Rename class from `PdfDownloadDomainServiceTest` → `PdfDownloadAdapterTest`
- Change field type from `PdfDownloadDomainService` → `PdfDownloadAdapter`
- Add import: `import com.wpanther.storage.application.port.out.PdfDownloadPort;`
- Add assertion: `assertThat(adapter).isInstanceOf(PdfDownloadPort.class);`

In `SagaOrchestrationServiceTest.java`:
- Update any `PdfDownloadDomainService` mock → `PdfDownloadPort` mock

**Step 4: Move infrastructure/adapter test files**

```bash
# inbound/messaging → in/messaging
mv src/test/java/com/wpanther/storage/infrastructure/adapter/inbound/messaging/SagaCommandAdapterTest.java \
   src/test/java/com/wpanther/storage/infrastructure/adapter/in/messaging/SagaCommandAdapterTest.java

# inbound/rest → in/rest
for f in AuthenticationControllerTest DocumentStorageControllerTest DocumentValidatorTest TestSecurityConfig; do
  mv "src/test/java/com/wpanther/storage/infrastructure/adapter/inbound/rest/${f}.java" \
     "src/test/java/com/wpanther/storage/infrastructure/adapter/in/rest/${f}.java" 2>/dev/null || \
  mv "src/test/java/com/wpanther/storage/infrastructure/adapter/inbound/rest/${f}.java" \
     "src/test/java/com/wpanther/storage/infrastructure/adapter/in/rest/${f}.java"
done

# security tests → in/security
for f in DocumentStorageUserDetailsServiceTest JwtAuthenticationAdapterTest JwtServiceTest; do
  mv "src/test/java/com/wpanther/storage/infrastructure/adapter/security/${f}.java" \
     "src/test/java/com/wpanther/storage/infrastructure/adapter/in/security/${f}.java" 2>/dev/null || true
done

# security/config → config/security
for f in JwtConfigValidatorTest SecurityConfigTest; do
  mv "src/test/java/com/wpanther/storage/infrastructure/adapter/security/config/${f}.java" \
     "src/test/java/com/wpanther/storage/infrastructure/config/security/${f}.java" 2>/dev/null || true
done

# security/exception
mv src/test/java/com/wpanther/storage/infrastructure/adapter/security/exception/SecurityExceptionTest.java \
   src/test/java/com/wpanther/storage/infrastructure/adapter/in/security/exception/SecurityExceptionTest.java 2>/dev/null || true

# outbound/messaging → out/messaging
mv src/test/java/com/wpanther/storage/infrastructure/adapter/outbound/messaging/MessagePublisherAdapterTest.java \
   src/test/java/com/wpanther/storage/infrastructure/adapter/out/messaging/MessagePublisherAdapterTest.java

# outbound/persistence → out/persistence
for f in MongoOutboxEventAdapterTest StoredDocumentEntityTest; do
  mv "src/test/java/com/wpanther/storage/infrastructure/adapter/outbound/persistence/${f}.java" \
     "src/test/java/com/wpanther/storage/infrastructure/adapter/out/persistence/${f}.java" 2>/dev/null || true
done

for f in JpaOutboxEventRepositoryTest OutboxEventEntityTest; do
  mv "src/test/java/com/wpanther/storage/infrastructure/adapter/outbound/persistence/outbox/${f}.java" \
     "src/test/java/com/wpanther/storage/infrastructure/adapter/out/persistence/outbox/${f}.java"
done

# outbound/storage → out/storage
for f in LocalFileStorageAdapterTest S3FileStorageAdapterTest; do
  mv "src/test/java/com/wpanther/storage/infrastructure/adapter/outbound/storage/${f}.java" \
     "src/test/java/com/wpanther/storage/infrastructure/adapter/out/storage/${f}.java"
done

# config/OutboxConfigTest → config/outbox/
mv src/test/java/com/wpanther/storage/infrastructure/config/OutboxConfigTest.java \
   src/test/java/com/wpanther/storage/infrastructure/config/outbox/OutboxConfigTest.java
```

**Step 5: Update package declarations in all moved test files**

For every moved file, update the package declaration to match its new directory path. Pattern:
```java
// Before (example):
package com.wpanther.storage.infrastructure.adapter.inbound.messaging;
// After:
package com.wpanther.storage.infrastructure.adapter.in.messaging;
```

Apply for every moved file using the directory path as the package name.

**Step 6: Update imports across all moved test files**

```bash
grep -rl "storage\.domain\.event\.\|storage\.domain\.port\.\|storage\.domain\.service\.\|\.adapter\.inbound\.\|\.adapter\.outbound\.\|infrastructure\.messaging\.\|infrastructure\.config\.[A-Z]" src/test/
```

Apply the complete import mapping (same as main source mapping from Tasks 1–6).

**Step 7: Run tests**

```bash
mvn test -q
```

Expected: BUILD SUCCESS, same test count as before.

### Task 8: Update JaCoCo exclusion patterns in `pom.xml`

**File:** `pom.xml`

**Step 1: Open pom.xml and find the `<excludes>` block**

```bash
grep -n "exclude\|jacoco" pom.xml | head -50
```

**Step 2: Update exclusion patterns**

Current exclusions referencing old paths:

| Old pattern | New pattern |
|---|---|
| `**/domain/event/*Event.class` | `**/application/dto/event/*Event.class` |
| `**/domain/event/*Command.class` | `**/application/dto/event/*Command.class` |
| `**/infrastructure/persistence/outbox/**` | `**/infrastructure/adapter/out/persistence/outbox/**` |
| `**/domain/model/*Builder.class` | unchanged (domain/model/ stays) |

> **Note:** Check the exact patterns in your `pom.xml` — the above are based on the JaCoCo config summary. Match by intent, not string equality. Only update patterns that actually exist.

**Step 3: Run full coverage verification**

```bash
mvn verify -q
```

Expected: BUILD SUCCESS, instruction ≥ 40%, branch ≥ 25%, class ≥ 50%.

If coverage drops:
```bash
mvn verify 2>&1 | grep -A 10 "Coverage check"
```

**Step 4: Commit**

```bash
git add src/test/java/com/wpanther/storage/ pom.xml
git rm -r src/test/java/com/wpanther/storage/domain/event/ 2>/dev/null || true
git rm -r src/test/java/com/wpanther/storage/domain/service/ 2>/dev/null || true
git rm -r src/test/java/com/wpanther/storage/infrastructure/adapter/inbound/ 2>/dev/null || true
git rm -r src/test/java/com/wpanther/storage/infrastructure/adapter/outbound/ 2>/dev/null || true
git rm -r src/test/java/com/wpanther/storage/infrastructure/adapter/security/ 2>/dev/null || true
git rm src/test/java/com/wpanther/storage/infrastructure/config/OutboxConfigTest.java 2>/dev/null || true
git add -u
git commit -m "Relocate test classes to mirror new hexagonal package structure, update JaCoCo exclusions"
```

---

## Phase 6 — Final Verification

### Task 9: Confirm clean state

**Step 1: No old package references in main source**

```bash
grep -r "storage\.domain\.event\."           src/main/ | grep "\.java:" && echo "FOUND" || echo "CLEAN"
grep -r "storage\.domain\.port\."            src/main/ | grep "\.java:" && echo "FOUND" || echo "CLEAN"
grep -r "storage\.domain\.service\."         src/main/ | grep "\.java:" && echo "FOUND" || echo "CLEAN"
grep -r "adapter\.inbound\.\|adapter\.outbound\." src/main/ | grep "\.java:" && echo "FOUND" || echo "CLEAN"
grep -r "infrastructure\.messaging\."        src/main/ | grep "\.java:" && echo "FOUND" || echo "CLEAN"
grep -r "infrastructure\.config\.[A-Z]"     src/main/ | grep "\.java:" && echo "FOUND" || echo "CLEAN"
```

Expected: all `CLEAN`.

**Step 2: No old directories remain**

```bash
find src/main/ -path "*/storage/domain/event*"    -type f 2>/dev/null
find src/main/ -path "*/storage/domain/port*"     -type f 2>/dev/null
find src/main/ -path "*/storage/domain/service*"  -type f 2>/dev/null
find src/main/ -path "*/adapter/inbound*"         -type f 2>/dev/null
find src/main/ -path "*/adapter/outbound*"        -type f 2>/dev/null
find src/main/ -path "*/infrastructure/messaging*" -type f 2>/dev/null
```

Expected: no output from any command.

**Step 3: Confirm new directories contain files**

```bash
find src/main/ -path "*/application/dto/event*" -name "*.java" | wc -l   # expect 10
find src/main/ -path "*/application/usecase*" -name "*.java" | wc -l      # expect 5
find src/main/ -path "*/application/port/out*" -name "*.java" | wc -l     # expect 4
find src/main/ -path "*/domain/repository*" -name "*.java" | wc -l        # expect 1
find src/main/ -path "*/adapter/in*" -name "*.java" | wc -l               # expect ~15
find src/main/ -path "*/adapter/out*" -name "*.java" | wc -l              # expect ~10
```

**Step 4: Run full test suite with coverage**

```bash
mvn verify
```

Expected: BUILD SUCCESS, instruction ≥ 40%, branch ≥ 25%, class ≥ 50%.

**Step 5: Done**

The document-storage-service now matches the canonical hexagonal layout. `domain/` contains only pure domain types. `application/` holds use-case contracts and implementations. `infrastructure/adapter/in/` and `out/` hold all adapters with canonical naming. `PdfDownloadAdapter` is properly isolated behind `PdfDownloadPort`. Config is organized by concern.
