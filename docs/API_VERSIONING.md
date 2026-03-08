# API Versioning Guide

## Overview

The Document Storage Service uses URL-based API versioning to support breaking changes without disrupting existing clients.

## Current Version

**API Version:** v1
**Base URL:** `http://localhost:8084/api/v1`
**Status:** Active

## Endpoints

### v1 Endpoints

| Resource | Base Path | Description |
|----------|-----------|-------------|
| Documents | `/api/v1/documents` | Document storage operations |
| Authentication | `/api/v1/auth` | JWT authentication and token management |
| Health | `/api/v1/health` | Service health check |

## Versioning Strategy

### URL Path Versioning

All API endpoints include the version in the URL path:

```
/api/{version}/{resource}
```

**Examples:**
- `/api/v1/documents` → Current version
- `/api/v2/documents` → Future version (when needed)

### Version Lifecycle

1. **Current** - Active development, recommended for all new integrations
2. **Supported** - Bug fixes only, no new features
3. **Deprecated** - Scheduled for removal, clients should migrate
4. **Retired** - No longer available

### Deprecation Policy

- **Minimum Notice Period:** 180 days (6 months)
- **Communication:** Changelog, email notifications, health check headers
- **Migration Support:** Migration guides and dual-version support during transition

## When to Bump Version

Create a new API version (v2, v3, etc.) when making **breaking changes**:

### Requires Version Bump

- ❌ Removing or renaming request/response fields
- ❌ Changing data types (e.g., String → Integer)
- ❌ Modifying authentication/authorization requirements
- ❌ Changing business logic behavior
- ❌ Removing entire endpoints

### Does NOT Require Version Bump

- ✅ Adding new optional fields
- ✅ Adding new endpoints
- ✅ Bug fixes that maintain contract
- ✅ Performance improvements
- ✅ Adding new HTTP headers

## Migration Guide Template

When introducing v2:

### Step 1: Announce Deprecation

```yaml
# application.yml
app.api.version:
  current: "v2"
  supported: "v1,v2"
  deprecated: "v1"
  deprecation-period-days: 180
```

### Step 2: Add Deprecation Headers

```java
@Deprecated(since = "v2", forRemoval = true)
@RequestMapping("/api/v1/documents")
public class DocumentStorageControllerV1 {
    // v1 implementation
}
```

### Step 3: Implement v2

```java
@RequestMapping("/api/v2/documents")
public class DocumentStorageControllerV2 {
    // v2 implementation with breaking changes
}
```

### Step 4: Communication

- Update API documentation
- Send deprecation notices to registered API users
- Add warning headers to v1 responses:
  ```http
  X-API-Deprecated: true
  X-API-Sunset: 2025-09-08
  Link: </api/v2/documents>; rel="successor-version"
  ```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `API_VERSION_CURRENT` | v1 | Current active API version |
| `API_VERSION_SUPPORTED` | v1 | Comma-separated list of supported versions |
| `API_VERSION_DEPRECATED` | (empty) | Comma-separated list of deprecated versions |
| `API_DEPRECATION_PERIOD_DAYS` | 180 | Minimum days before removing deprecated version |
| `API_HEADER_CHECK_ENABLED` | false | Enable X-API-Version header validation |

### Example Configuration

```yaml
app:
  api:
    version:
      current: v2
      supported: v1,v2,v3
      deprecated: v1
      deprecation-period-days: 180
```

## Best Practices

1. **Backward Compatibility** - Maintain old versions for at least 6 months
2. **Clear Communication** - Document all breaking changes in migration guides
3. **Graceful Deprecation** - Provide clear migration paths and examples
4. **Version Isolation** - Each version should have its own controller
5. **Testing** - Maintain separate test suites for each supported version

## Client Integration

### Specifying API Version

Clients specify the version via URL path:

```bash
# Using v1
curl -X GET http://localhost:8084/api/v1/documents/{id}

# Using v2 (when available)
curl -X GET http://localhost:8084/api/v2/documents/{id}
```

### Version Detection (Optional)

Clients can optionally include version in headers:

```bash
curl -X GET http://localhost:8084/api/v1/documents/{id} \
  -H "X-API-Version: v1"
```

When `API_HEADER_CHECK_ENABLED=true`, the service validates the header matches the URL path.

## Monitoring

### Health Check Response

```json
{
  "status": "UP",
  "components": {
    "api": {
      "status": "UP",
      "details": {
        "currentVersion": "v1",
        "supportedVersions": ["v1"],
        "deprecatedVersions": []
      }
    }
  }
}
```

## References

- [Spring Boot Versioning Best Practices](https://spring.io/blog/2009/03/08/restful-spring-revisited/)
- [API Versioning Strategies](https://www.vinaysahni.com/best-practices-for-a-pragmatic-restful-api)
- [Semantic Versioning](https://semver.org/)
