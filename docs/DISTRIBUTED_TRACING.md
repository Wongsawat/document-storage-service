# Distributed Tracing Guide

## Overview

The Document Storage Service uses **OpenTelemetry** for distributed tracing, enabling end-to-end visibility across microservices in the Thai e-Tax invoice processing pipeline.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Distributed Tracing Flow                            │
└─────────────────────────────────────────────────────────────────────────────┘

Client Request
     ↓
Document Storage Service (export spans)
     ↓
OpenTelemetry SDK (batch, sample)
     ↓
OTLP Exporter
     ↓
OpenTelemetry Collector / Jaeger / Tempo
     ↓
Visualization UI (Jaeger UI / Grafana Tempo)
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `TRACING_ENABLED` | true | Enable/disable distributed tracing |
| `TRACING_SAMPLING_PROBABILITY` | 0.1 | Fraction of traces to sample (0.0-1.0) |
| `OTEL_ENDPOINT` | http://localhost:4317 | OTLP gRPC collector endpoint |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | http://localhost:4317/v1/traces | OTLP traces endpoint |
| `OTEL_TRACES_EXPORTER` | otlp | Exporter: otlp, logging, or none |
| `OTEL_TRACES_SAMPLER_TYPE` | traceidratio | Sampler type |
| `OTEL_TRACES_SAMPLER_RATIO` | 0.1 | Sampling ratio (10%) |
| `OTEL_METRICS_EXPORTER` | otlp | Metrics exporter |
| `DEPLOYMENT_ENV` | local | Deployment environment label |

### Application Configuration

```yaml
# application.yml
management:
  tracing:
    enabled: ${TRACING_ENABLED:true}
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:0.1}
  otlp:
    tracing:
      endpoint: ${OTEL_ENDPOINT:http://localhost:4317}

otel:
  resource:
    attributes:
      service.name: ${spring.application.name}
      service.version: ${info.version:1.0.0-SNAPSHOT}
      deployment.environment: ${DEPLOYMENT_ENV:local}
  traces:
    exporter: ${OTEL_TRACES_EXPORTER:otlp}
    sampler:
      type: ${OTEL_TRACES_SAMPLER_TYPE:traceidratio}
      ratio: ${OTEL_TRACES_SAMPLER_RATIO:0.1}
```

## Quick Start

### Option 1: Jaeger (Local Development)

```bash
# Start Jaeger All-in-One
docker run -d --name jaeger \
  -e COLLECTOR_OTLP_ENABLED=true \
  -p 4317:4317 \
  -p 4318:4318 \
  -p 16686:16686 \
  jaegertracing/all-in-one:latest

# Run the service
mvn spring-boot:run

# Access Jaeger UI
open http://localhost:16686
```

### Option 2: Grafana Tempo (Production)

```yaml
# docker-compose.yml
version: "3"
services:
  tempo:
    image: grafana/tempo:latest
    command: ["--config.file=/etc/tempo.yaml"]
    volumes:
      - ./tempo.yaml:/etc/tempo.yaml
    ports:
      - "4317:4317"  # OTLP gRPC
      - "3200:3200"  # Tempo query API

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_INSTALL_PLUGINS=grafana-tempo-datasource
```

### Option 3: OpenTelemetry Collector (Recommended for Production)

```yaml
# otel-collector-config.yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317

processors:
  batch:
    timeout: 30s
    max_queue_size: 2048
    max_export_batch_size: 512

exporters:
  jaeger:
    endpoint: jaeger:14250
    tls:
      insecure: true

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [jaeger]
```

## Span Attributes

### Automatic Instrumentation

OpenTelemetry Spring Boot starter automatically instruments:

- **HTTP Server**: REST endpoints (DocumentStorageController, AuthenticationController)
- **HTTP Client**: Outbound HTTP requests (PdfDownloadService)
- **MongoDB**: Database queries
- **Kafka**: Message publishing/consumption (Apache Camel routes)

### Manual Instrumentation

Add custom spans using `@NewSpan` or `Span`:

```java
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.annotations.WithSpan;

@Service
public class DocumentStorageService {

    @WithSpan("DocumentStorageService.storeDocument")
    public StoredDocument storeDocument(MultipartFile file, String invoiceId) {
        Span span = Span.current();
        span.setAttribute("document.filename", file.getOriginalFilename());
        span.setAttribute("document.size", file.getSize());
        span.setAttribute("invoice.id", invoiceId);

        // Business logic...

        return document;
    }
}
```

### Key Span Names

| Component | Span Name | Attributes |
|-----------|-----------|------------|
| DocumentStorageController | `HTTP POST /api/v1/documents` | `http.method`, `http.url`, `http.status_code` |
| PdfDownloadService | `PdfDownloadService.downloadPdf` | `pdf.url`, `pdf.size` |
| MongoDocumentAdapter | `MongoDB find/insert/update` | `db.system`, `db.name`, `db.operation` |
| SagaCommandHandler | `SagaCommandHandler.handleProcessCommand` | `saga.command.type`, `saga.command.id` |

## Trace Context Propagation

### HTTP Headers

Trace context is propagated via W3C Trace Context headers:

```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
tracestate: rojo=00f067aa0ba902b7,congo=t61rcWkgMzE
```

### Kafka Headers

Kafka messages include trace context:

```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
```

## Cross-Service Tracing

Document Storage Service participates in distributed traces with:

1. **Orchestrator Service** (port 8093)
   - Sends saga commands via Kafka
   - Trace context propagated through Kafka headers

2. **PDF Signing Service** (port 8087)
   - HTTP calls from PdfDownloadService
   - Trace context via HTTP headers

3. **Tax Invoice PDF Generation** (port 8089)
   - Downloads PDFs from MinIO
   - Trace context links PDF generation with storage

## Sampling Strategies

### Development (100% Sampling)

```bash
export TRACING_SAMPLING_PROBABILITY=1.0
export OTEL_TRACES_SAMPLER_RATIO=1.0
```

### Production (Head Sampling)

```bash
export TRACING_SAMPLING_PROBABILITY=0.01  # 1% sampling
export OTEL_TRACES_SAMPLER_RATIO=0.01
```

### Adaptive Sampling

Sample based on service, operation, or error status:

```yaml
otel:
  traces:
    sampler:
      type: parentbased_traceidratio
      ratio: 0.1
```

## Performance Considerations

### Overhead

- **CPU**: ~2-5% with 10% sampling
- **Memory**: ~50-100MB for span buffering
- **Network**: ~1KB per exported span

### Optimization Tips

1. **Adjust Batch Size**: Increase `max_export_batch_size` for high-throughput scenarios
2. **Tune Sampling**: Use lower sampling rates in production
3. **Filter Spans**: Exclude health checks and actuator endpoints
4. **Async Export**: Batch exporter is enabled by default

### Filtering Noisy Spans

```yaml
management:
  tracing:
    enabled: true
    sampling:
      probability: 0.1
    # Exclude specific paths
    exclude:
      - /actuator/health
      - /actuator/info
```

## Troubleshooting

### No Traces Appearing

1. **Check OTLP Endpoint**: Verify collector is reachable
   ```bash
   curl http://localhost:4317/v1/status
   ```

2. **Enable Debug Logging**:
   ```yaml
   logging:
     level:
       io.opentelemetry: DEBUG
   ```

3. **Test with Logging Exporter**:
   ```bash
   export OTEL_TRACES_EXPORTER=logging
   ```

### Missing Cross-Service Context

1. Verify Kafka trace propagation is enabled
2. Check HTTP client instrumentation is active
3. Ensure W3C trace context headers are not filtered

### High Memory Usage

1. Reduce `max_queue_size`
2. Lower sampling probability
3. Decrease `timeout` on batch processor

## Monitoring Traces

### Jaeger Queries

```
# Find all traces for a specific document
operation.name:"DocumentStorageService.storeDocument" AND document.id:"abc123"

# Find slow operations
duration > 1000ms

# Find errors
error:true
```

### Grafana Tempo Queries

```
# Trace search by service name
{service.name="document-storage-service"}

# Trace search by span name
{name="HTTP GET /api/v1/documents/{id}"}
```

## Best Practices

1. **Always include business context** in span attributes:
   - `invoice.id`, `document.id`, `saga.id`
   - `document.type`, `storage.provider`

2. **Use consistent naming** for spans:
   - Prefix with service: `DocumentStorageService.storeDocument`
   - Use action verbs: `store`, `download`, `delete`

3. **Add events to spans** for significant moments:
   ```java
   Span.current().addEvent("document_stored",
       Attributes.of(
           "document.id", documentId,
           "storage.url", storageUrl
       )
   );
   ```

4. **Mark errors with status**:
   ```java
   try {
       // operation
   } catch (Exception e) {
       Span.current().recordException(e);
       Span.current().setStatus(StatusCode.ERROR, e.getMessage());
       throw e;
   }
   ```

## References

- [OpenTelemetry Spring Boot Starter](https://opentelemetry.io/docs/instrumentation/java/automatic/agent-config/)
- [W3C Trace Context](https://www.w3.org/TR/trace-context/)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [Grafana Tempo](https://grafana.com/docs/tempo/latest/)
- [Spring Boot Observability](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.observability)
