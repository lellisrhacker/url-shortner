package com.urlshortener.service;

import com.urlshortener.config.AppProperties;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.entity.UrlMapping;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.util.Base62;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Core logic: create short links and look them up again.
 *
 * Lookups use the cache-aside pattern:
 *   1. check Redis first
 *   2. on a miss, read from MySQL and store the result in Redis with a TTL
 * so popular links are served from memory and do not hit the database every time.
 */
@Service
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);
    private static final String CACHE_PREFIX = "url:";
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final int MAX_URL_LENGTH = 2048;

    private final UrlRepository repository;
    private final StringRedisTemplate redis;
    private final AppProperties properties;

    public UrlService(UrlRepository repository, StringRedisTemplate redis, AppProperties properties) {
        this.repository = repository;
        this.redis = redis;
        this.properties = properties;
    }

    public ShortenResponse shorten(String originalUrl) {
        String url = originalUrl.trim();
        validate(url);

        String code = generateUniqueCode();
        repository.save(new UrlMapping(code, url));
        cachePut(code, url);

        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        return new ShortenResponse(code, baseUrl + "/" + code, url);
    }

    public String resolve(String shortCode) {
        String cached = cacheGet(shortCode);
        if (cached != null) {
            return cached;
        }

        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        cachePut(shortCode, mapping.getOriginalUrl());
        return mapping.getOriginalUrl();
    }

    private void validate(String url) {
        if (url.length() > MAX_URL_LENGTH) {
            throw new InvalidUrlException("URL is too long");
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            boolean httpScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            if (!httpScheme || uri.getHost() == null) {
                throw new InvalidUrlException("URL must start with http:// or https:// and contain a host");
            }
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("URL is not valid");
        }
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = Base62.randomCode(properties.getCodeLength());
            if (!repository.existsByShortCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique short code");
    }

    // ---- cache helpers: if Redis is down the app still works, just straight from MySQL ----

    private String cacheGet(String shortCode) {
        try {
            return redis.opsForValue().get(CACHE_PREFIX + shortCode);
        } catch (Exception e) {
            log.warn("Redis read failed, falling back to database: {}", e.getMessage());
            return null;
        }
    }

    private void cachePut(String shortCode, String originalUrl) {
        try {
            redis.opsForValue().set(
                    CACHE_PREFIX + shortCode, originalUrl, Duration.ofMinutes(properties.getCacheTtlMinutes()));
        } catch (Exception e) {
            log.warn("Redis write failed, continuing without cache: {}", e.getMessage());
        }
    }
}
