package com.wpanther.storage.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Configuration for HTTP client used to download PDFs from signing service.
 *
 * Provides a configured HttpClient bean with appropriate timeouts
 * and redirect handling for downloading signed PDFs.
 */
@Configuration
public class HttpClientConfig {

    /**
     * Creates and configures an HttpClient bean for downloading PDFs.
     *
     * Configuration:
     * - 30 second connection timeout
     * - Automatic redirect following (for HTTP → HTTPS or URL rewrites)
     * - Connection pool reuse via HTTP/2 where available
     *
     * @return a configured HttpClient instance
     */
    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }
}
