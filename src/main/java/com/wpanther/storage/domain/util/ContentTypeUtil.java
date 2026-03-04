package com.wpanther.storage.domain.util;

/**
 * Utility class for determining content types based on file extensions.
 */
public final class ContentTypeUtil {

    private ContentTypeUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Determine content type from filename extension.
     *
     * @param filename the filename to analyze (may be null)
     * @return the MIME content type (defaults to "application/octet-stream")
     */
    public static String determineContentType(String filename) {
        if (filename == null) {
            return "application/octet-stream";
        }

        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lower.endsWith(".xml")) {
            return "application/xml";
        } else if (lower.endsWith(".json")) {
            return "application/json";
        } else if (lower.endsWith(".png")) {
            return "image/png";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lower.endsWith(".gif")) {
            return "image/gif";
        } else if (lower.endsWith(".txt")) {
            return "text/plain";
        } else if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html";
        } else if (lower.endsWith(".csv")) {
            return "text/csv";
        }

        return "application/octet-stream";
    }
}
