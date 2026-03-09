# Custom Business Metrics Guide

**Document Storage Service - Custom Prometheus Metrics**

**Date:** 2026-03-09
**Version:** 1.0.0
**Service:** document-storage-service (Port 8084)

---

## Overview

This service exposes **custom business metrics** to Prometheus beyond standard JVM metrics. These metrics provide visibility into document storage operations, PDF download performance, orphaned document detection, and reconciliation runs.

**Access metrics:**
```bash
curl http://localhost:8084/actuator/prometheus
```

**Prometheus configuration:**
```yaml
scrape_configs:
  - job_name: 'document-storage-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8084']
```

---

## Available Metrics

### Document Storage Metrics

#### `document_storage_stored_total`

**Type:** Counter
**Description:** Total number of documents stored (cumulative)

**Labels:**
- `service`: Always "document-storage"
- `document_type`: Document type (invoice_pdf, invoice_xml, signed_xml, unsigned_pdf, attachment, other)

**Example:**
```
document_storage_stored_total{service="document-storage",document_type="invoice_pdf",} 42.0
```

**Use cases:**
- Track document storage volume by type
- Monitor storage trends over time
- Alert on unexpected storage spikes

**PromQL queries:**
```promql
# Total documents stored
sum(document_storage_stored_total)

# Documents stored per type
sum by (document_type) (document_storage_stored_total)

# Rate of documents stored per minute
rate(document_storage_stored_total[1m])
```

---

#### `document_storage_deleted_total`

**Type:** Counter
**Description:** Total number of documents deleted (cumulative)

**Labels:**
- `service`: Always "document-storage"

**Example:**
```
document_storage_deleted_total{service="document-storage",} 5.0
```

**Use cases:**
- Track document deletion volume
- Monitor cleanup job effectiveness

**PromQL queries:**
```promql
# Total documents deleted
sum(document_storage_deleted_total)

# Rate of deletions per minute
rate(document_storage_deleted_total[1m])
```

---

#### `document_storage_retrieved_total`

**Type:** Counter
**Description:** Total number of documents retrieved (cumulative)

**Labels:**
- `service`: Always "document-storage"

**Example:**
```
document_storage_retrieved_total{service="document-storage",} 156.0
```

**Use cases:**
- Track document retrieval volume
- Monitor read patterns

**PromQL queries:**
```promql
# Total documents retrieved
sum(document_storage_retrieved_total)

# Rate of retrievals per minute
rate(document_storage_retrieved_total[1m])
```

---

### Storage Duration Metrics

#### `document_storage_storage_duration_seconds`

**Type:** Timer (Histogram)
**Description:** Time taken to store documents (with percentiles)

**Labels:**
- `service`: Always "document-storage"
- `operation`: Always "storage"
- `le`: Less-than-or-equal boundary (for histogram)

**Percentiles published:** p50, p95, p99
**SLA boundaries:** 100ms, 500ms, 1s, 5s

**Examples:**
```
document_storage_storage_duration_seconds_count{service="document-storage",operation="storage",} 42.0
document_storage_storage_duration_seconds_sum{service="document-storage",operation="storage",} 15.234
document_storage_storage_duration_seconds{service="document-storage",operation="storage",le="0.1",} 35.0
document_storage_storage_duration_seconds{service="document-storage",operation="storage",le="0.5",} 40.0
document_storage_storage_duration_seconds{service="document-storage",operation="storage",le="1.0",} 41.0
document_storage_storage_duration_seconds{service="document-storage",operation="storage",le="5.0",} 42.0

# Percentile summaries
document_storage_storage_duration_seconds_max{service="document-storage",operation="storage",quantile="0.5",} 0.234
document_storage_storage_duration_seconds_max{service="document-storage",operation="storage",quantile="0.95",} 0.789
document_storage_storage_duration_seconds_max{service="document-storage",operation="storage",quantile="0.99",} 1.234
```

**Use cases:**
- Monitor storage operation latency
- Detect performance degradation
- Track p95/p99 SLA compliance

**PromQL queries:**
```promql
# Average storage duration
rate(document_storage_storage_duration_seconds_sum[5m]) / rate(document_storage_storage_duration_seconds_count[5m])

# P95 storage latency
histogram_quantile(0.95, rate(document_storage_storage_duration_seconds_bucket[5m]))

# P99 storage latency
histogram_quantile(0.99, rate(document_storage_storage_duration_seconds_bucket[5m]))

# Storage operations exceeding 1s SLA
rate(document_storage_storage_duration_seconds_bucket{le="1.0"}[5m])
```

---

### PDF Download Metrics

#### `document_storage_pdf_download_success_total`

**Type:** Counter
**Description:** Total number of successful PDF downloads (cumulative)

**Labels:**
- `service`: Always "document-storage"
- `operation`: Always "pdf_download"
- `status`: Always "success"

**Example:**
```
document_storage_pdf_download_success_total{service="document-storage",operation="pdf_download",status="success",} 38.0
```

---

#### `document_storage_pdf_download_failure_total`

**Type:** Counter
**Description:** Total number of failed PDF downloads (cumulative)

**Labels:**
- `service`: Always "document-storage"
- `operation`: Always "pdf_download"
- `status`: Always "failure"

**Example:**
```
document_storage_pdf_download_failure_total{service="document-storage",operation="pdf_download",status="failure",} 2.0
```

**Use cases:**
- Monitor PDF download reliability
- Calculate success rate
- Detect external service issues (MinIO)

**PromQL queries:**
```promql
# Total PDF downloads (success + failure)
sum(document_storage_pdf_download_success_total) + sum(document_storage_pdf_download_failure_total)

# PDF download success rate
sum(document_storage_pdf_download_success_total) / (sum(document_storage_pdf_download_success_total) + sum(document_storage_pdf_download_failure_total)) * 100

# PDF download failure rate
rate(document_storage_pdf_download_failure_total[5m])
```

---

#### `document_storage_pdf_download_duration_seconds`

**Type:** Timer (Histogram)
**Description:** Time taken to download PDFs from MinIO (with percentiles)

**Labels:**
- `service`: Always "document-storage"
- `operation`: Always "pdf_download"
- `le`: Less-than-or-equal boundary (for histogram)

**Percentiles published:** p50, p95, p99
**SLA boundaries:** 100ms, 500ms, 1s, 5s, 10s

**Examples:**
```
document_storage_pdf_download_duration_seconds_count{service="document-storage",operation="pdf_download",} 40.0
document_storage_pdf_download_duration_seconds_sum{service="document-storage",operation="pdf_download",} 12.456
document_storage_pdf_download_duration_seconds_max{service="document-storage",operation="pdf_download",quantile="0.95",} 0.567
document_storage_pdf_download_duration_seconds_max{service="document-storage",operation="pdf_download",quantile="0.99",} 1.234
```

**Use cases:**
- Monitor PDF download latency
- Detect MinIO performance issues
- Track network latency trends

**PromQL queries:**
```promql
# Average PDF download duration
rate(document_storage_pdf_download_duration_seconds_sum[5m]) / rate(document_storage_pdf_download_duration_seconds_count[5m])

# P95 PDF download latency
histogram_quantile(0.95, rate(document_storage_pdf_download_duration_seconds_bucket[5m]))

# P99 PDF download latency
histogram_quantile(0.99, rate(document_storage_pdf_download_duration_seconds_bucket[5m]))
```

---

### Orphaned Document Metrics

#### `document_storage_orphaned_documents`

**Type:** Gauge
**Description:** Current number of orphaned documents (not cumulative)

**Labels:**
- `service`: Always "document-storage"
- `status`: Always "orphaned"

**Example:**
```
document_storage_orphaned_documents{service="document-storage",status="orphaned",} 3.0
```

**Use cases:**
- Monitor orphaned document count
- Detect dual-write consistency issues
- Alert on orphan accumulation

**PromQL queries:**
```promql
# Current orphaned documents
document_storage_orphaned_documents{service="document-storage",status="orphaned"}

# Alert if orphaned documents > 0 for 5 minutes
document_storage_orphaned_documents{service="document-storage",status="orphaned"} > 0
```

---

### Reconciliation Metrics

#### `document_storage_reconciliation_run_total`

**Type:** Counter
**Description:** Total number of reconciliation runs (cumulative)

**Labels:**
- `service`: Always "document-storage"
- `operation`: Always "reconciliation"

**Example:**
```
document_storage_reconciliation_run_total{service="document-storage",operation="reconciliation",} 156.0
```

**Use cases:**
- Monitor reconciliation job execution
- Detect missed scheduled runs

**PromQL queries:**
```promql
# Total reconciliation runs
sum(document_storage_reconciliation_run_total)

# Reconciliation runs per hour
rate(document_storage_reconciliation_run_total[1h])
```

---

#### `document_storage_reconciliation_orphans_found_total`

**Type:** Counter
**Description:** Total number of orphaned documents found (cumulative)

**Labels:**
- `service`: Always "document-storage"
- `operation`: Always "reconciliation"

**Example:**
```
document_storage_reconciliation_orphans_found_total{service="document-storage",operation="reconciliation",} 8.0
```

**Use cases:**
- Track total orphans found over time
- Monitor dual-write consistency trends

**PromQL queries:**
```promql
# Total orphans found
sum(document_storage_reconciliation_orphans_found_total)

# Orphans found per reconciliation run
rate(document_storage_reconciliation_orphans_found_total[15m])
```

---

## Grafana Dashboard Examples

### Document Storage Overview

**Panels:**

1. **Documents Stored (Rate)**
   ```promql
   sum(rate(document_storage_stored_total[5m])) by (document_type)
   ```
   Visualization: Bar chart
   Description: Documents stored per minute by type

2. **Storage Latency (P95/P99)**
   ```promql
   histogram_quantile(0.95, sum(rate(document_storage_storage_duration_seconds_bucket[5m])) by (le))
   histogram_quantile(0.99, sum(rate(document_storage_storage_duration_seconds_bucket[5m])) by (le))
   ```
   Visualization: Time series graph
   Description: p95 and p99 storage latency percentiles

3. **PDF Download Success Rate**
   ```promql
   sum(document_storage_pdf_download_success_total) / (sum(document_storage_pdf_download_success_total) + sum(document_storage_pdf_download_failure_total)) * 100
   ```
   Visualization: Stat/Gauge
   Description: PDF download success rate percentage

4. **Orphaned Documents**
   ```promql
   document_storage_orphaned_documents{service="document-storage",status="orphaned"}
   ```
   Visualization: Stat/Gauge
   Description: Current orphaned document count (alert if > 0)

---

## Alerting Rules

### Critical Alerts

```yaml
groups:
  - name: document_storage_service
    rules:
      # High orphaned document count
      - alert: HighOrphanedDocumentCount
        expr: document_storage_orphaned_documents{service="document-storage",status="orphaned"} > 10
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High orphaned document count in document-storage-service"
          description: "{{ $value }} orphaned documents detected"

      # PDF download success rate below 95%
      - alert: LowPdfDownloadSuccessRate
        expr: |
          sum(document_storage_pdf_download_success_total) /
          (sum(document_storage_pdf_download_success_total) + sum(document_storage_pdf_download_failure_total)) * 100 < 95
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Low PDF download success rate in document-storage-service"
          description: "{{ $value | humanize }}% success rate (below 95% threshold)"

      # Storage P99 latency exceeds 5s
      - alert: HighStorageLatency
        expr: |
          histogram_quantile(0.99,
            sum(rate(document_storage_storage_duration_seconds_bucket[5m])) by (le)) > 5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High storage latency in document-storage-service"
          description: "P99 storage latency is {{ $value | humanizeDuration }} (exceeds 5s threshold)"

      # Reconciliation job not running
      - alert: ReconciliationJobNotRunning
        expr: |
          time() - max(document_storage_reconciliation_run_total{service="document-storage"}) > 1200
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Reconciliation job not running in document-storage-service"
          description: "No reconciliation runs in the last 20 minutes"
```

---

## Implementation Details

### Metrics Service Class

**Location:** `com.wpanther.storage.infrastructure.config.DocumentStorageMetricsService`

**Key methods:**
- `recordDocumentStored(DocumentType type)` - Record document storage
- `recordDocumentDeleted()` - Record document deletion
- `recordDocumentRetrieved()` - Record document retrieval
- `recordPdfDownloadSuccess(long durationMillis)` - Record successful PDF download
- `recordPdfDownloadFailure(long durationMillis)` - Record failed PDF download
- `recordReconciliationRun(long orphanedFound)` - Record reconciliation run
- `updateOrphanedDocumentsCount(long count)` - Update orphaned document gauge

### Metrics Configuration

**Configuration class:** `com.wpanther.storage.infrastructure.config.MetricsConfig`

**Bean:** `documentStorageMetricsService(MeterRegistry meterRegistry)`

### Integration Points

Metrics are recorded at the following locations:

| Service Method | Metric Recorded |
|----------------|-----------------|
| `FileStorageDomainService.storeDocument()` | `document_storage_stored_total`, `document_storage_storage_duration_seconds` |
| `FileStorageDomainService.deleteDocument()` | `document_storage_deleted_total` |
| `FileStorageDomainService.getDocument()` | `document_storage_retrieved_total` |
| `FileStorageDomainService.getDocumentContent()` | `document_storage_retrieved_total` |
| `PdfDownloadDomainService.downloadPdf()` | `document_storage_pdf_download_success_total`, `document_storage_pdf_download_failure_total`, `document_storage_pdf_download_duration_seconds` |
| `OutboxReconciliationService.reconcileOrphanedDocuments()` | `document_storage_reconciliation_run_total`, `document_storage_reconciliation_orphans_found_total`, `document_storage_orphaned_documents` |

---

## Testing Metrics

### Verify Metrics Endpoint

```bash
# Check that metrics are exposed
curl http://localhost:8084/actuator/prometheus | grep document_storage

# Check specific metric
curl http://localhost:8084/actuator/prometheus | grep document_storage_stored_total
```

### Load Testing with Metrics

```bash
# Store a document (generates metrics)
curl -X POST http://localhost:8084/api/v1/documents \
  -F "file=@test.pdf" \
  -F "invoiceId=INV-001" \
  -F "documentType=INVOICE_PDF"

# Check metrics increased
curl http://localhost:8084/actuator/prometheus | grep document_storage_stored_total
```

### Prometheus Query Testing

```bash
# Query Prometheus directly
curl 'http://localhost:9090/api/v1/query?query=document_storage_stored_total'

# Query rate of documents stored
curl 'http://localhost:9090/api/v1/query?query=rate(document_storage_stored_total[5m])'
```

---

## Troubleshooting

### Metrics Not Appearing

1. **Check actuator endpoint is enabled:**
   ```bash
   curl http://localhost:8084/actuator
   ```

2. **Verify Prometheus endpoint:**
   ```bash
   curl http://localhost:8084/actuator/prometheus
   ```

3. **Check metrics in service logs:**
   ```
   Custom business metrics initialized and registered with Micrometer
   ```

4. **Verify MeterRegistry bean:**
   - Check that `MetricsConfig` is loaded by Spring
   - Look for "documentStorageMetricsService" bean in logs

### Unexpected Metric Values

1. **Check document type labels:**
   - Ensure `DocumentType` enum values match expected labels
   - Labels are lowercase (e.g., "invoice_pdf" not "INVOICE_PDF")

2. **Verify timers are stopped:**
   - Timer samples must be stopped with `stopStorageOperation(Timer.Sample)`
   - Check that exceptions don't bypass timer stop

3. **Validate gauge updates:**
   - Orphaned document gauge is updated by `OutboxReconciliationService`
   - Check reconciliation job is running (every 15 minutes by default)

---

## Performance Considerations

1. **Metrics overhead:** Micrometer metrics have minimal overhead (~1-2 microseconds per operation)
2. **Percentile computation:** Published percentiles (p50, p95, p99) are computed client-side by Micrometer
3. **Histogram buckets:** SLA boundaries (100ms, 500ms, 1s, 5s) create additional time series
4. **Cardinality:** Document type labels keep metric cardinality manageable (6 types max)

---

## References

- [Micrometer Documentation](https://micrometer.io/docs)
- [Prometheus Best Practices](https://prometheus.io/docs/practices/naming/)
- [Spring Boot Actuator Metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics)
- [Document Storage Service Architecture](../ARCHITECTURE_REVIEW.md)
