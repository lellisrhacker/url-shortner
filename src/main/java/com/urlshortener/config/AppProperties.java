package com.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Custom settings from application.properties (prefix "app").
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Public address of this service, used to build the short link returned to the client. */
    private String baseUrl = "http://localhost:8080";

    /** How many minutes a short code stays in the Redis cache. */
    private long cacheTtlMinutes = 60;

    /** Number of characters in a generated short code. */
    private int codeLength = 7;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public long getCacheTtlMinutes() {
        return cacheTtlMinutes;
    }

    public void setCacheTtlMinutes(long cacheTtlMinutes) {
        this.cacheTtlMinutes = cacheTtlMinutes;
    }

    public int getCodeLength() {
        return codeLength;
    }

    public void setCodeLength(int codeLength) {
        this.codeLength = codeLength;
    }
}
