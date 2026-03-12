package com.wpanther.storage.infrastructure.adapter.in.rest.config;

/**
 * API version constants for REST endpoint versioning.
 * <p>
 * This service uses URL-based versioning to support breaking changes without
 * disrupting existing clients. All endpoints are prefixed with {@code /api/{version}/}.
 * </p>
 * <p>
 * <b>Versioning Strategy:</b>
 * <ul>
 *   <li>Use URL path versioning: {@code /api/v1/resources}, {@code /api/v2/resources}</li>
 *   <li>Maintain backward compatibility by keeping old versions operational</li>
 *   <li>Deprecate old versions with at least 6 months notice</li>
 *   <li>Document breaking changes in migration guides</li>
 *   <li>Use Semantic Versioning for major API changes</li>
 * </ul>
 * </p>
 * <p>
 * <b>When to bump version:</b>
 * <ul>
 *   <li>Remove or rename request/response fields</li>
 *   <li>Change data types (e.g., String to Integer)</li>
 *   <li>Modify authentication/authorization requirements</li>
 *   <li>Change business logic behavior</li>
 *   <li>Remove entire endpoints</li>
 * </ul>
 * </p>
 * <p>
 * <b>What does NOT require version bump:</b>
 * <ul>
 *   <li>Adding new optional fields</li>
 *   <li>Adding new endpoints</li>
 *   <li>Bug fixes that maintain contract</li>
 *   <li>Performance improvements</li>
 * </ul>
 * </p>
 */
public final class ApiVersion {

    private ApiVersion() {
        // Utility class - prevent instantiation
    }

    /**
     * Current API version (v1).
     * <p>
     * All current endpoints use this version:
     * <ul>
     *   <li>{@code /api/v1/documents/*} - Document storage operations</li>
     *   <li>{@code /api/v1/auth/*} - Authentication and token management</li>
     * </ul>
     * </p>
     */
    public static final String V1 = "v1";

    /**
     * API base path prefix.
     * <p>
     * Format: {@code /api/{version}/}
     * </p>
     */
    public static final String BASE_PATH = "/api/";

    /**
     * Current documents endpoint path.
     */
    public static final String DOCUMENTS = BASE_PATH + V1 + "/documents";

    /**
     * Current authentication endpoint path.
     */
    public static final String AUTH = BASE_PATH + V1 + "/auth";

    /**
     * Current health endpoint path.
     */
    public static final String HEALTH = BASE_PATH + V1 + "/health";

    /**
     * Get the full path for an endpoint.
     *
     * @param version the API version (e.g., "v1", "v2")
     * @param resource the resource path (e.g., "documents", "auth")
     * @return the full endpoint path
     */
    public static String path(String version, String resource) {
        return BASE_PATH + version + "/" + resource;
    }
}
