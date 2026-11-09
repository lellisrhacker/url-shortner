package com.urlshortener.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.urlshortener.config.AppProperties;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.entity.UrlMapping;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.UrlRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlRepository repository;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private UrlService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.setBaseUrl("http://short.test/");
        properties.setCacheTtlMinutes(60);
        properties.setCodeLength(7);

        service = new UrlService(repository, redis, properties);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    // ---------- shorten ----------

    @Test
    void shortenSavesMappingAndReturnsShortUrl() {
        when(repository.existsByShortCode(anyString())).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortenResponse response = service.shorten("https://example.com/some/very/long/path");

        assertEquals(7, response.shortCode().length());
        assertEquals("http://short.test/" + response.shortCode(), response.shortUrl());
        assertEquals("https://example.com/some/very/long/path", response.originalUrl());
        verify(repository).save(any(UrlMapping.class));
    }

    @Test
    void shortenStoresNewLinkInCacheWithTtl() {
        when(repository.existsByShortCode(anyString())).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortenResponse response = service.shorten("https://example.com/a");

        verify(valueOps).set("url:" + response.shortCode(), "https://example.com/a", Duration.ofMinutes(60));
    }

    @Test
    void shortenRetriesWhenGeneratedCodeIsAlreadyTaken() {
        when(repository.existsByShortCode(anyString())).thenReturn(true, false);
        when(repository.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortenResponse response = service.shorten("https://example.com/a");

        assertEquals(7, response.shortCode().length());
        verify(repository, org.mockito.Mockito.times(2)).existsByShortCode(anyString());
    }

    @Test
    void shortenTrimsWhitespaceAroundUrl() {
        when(repository.existsByShortCode(anyString())).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortenResponse response = service.shorten("   https://example.com/a  ");

        assertEquals("https://example.com/a", response.originalUrl());
    }

    @Test
    void shortenRejectsInvalidUrls() {
        assertThrows(InvalidUrlException.class, () -> service.shorten("not a url"));
        assertThrows(InvalidUrlException.class, () -> service.shorten("ftp://example.com/file"));
        assertThrows(InvalidUrlException.class, () -> service.shorten("example.com"));
        assertThrows(InvalidUrlException.class, () -> service.shorten("http://"));
        assertThrows(InvalidUrlException.class, () -> service.shorten("javascript:alert(1)"));

        verify(repository, never()).save(any());
    }

    @Test
    void shortenStillWorksWhenRedisIsDown() {
        when(repository.existsByShortCode(anyString())).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("redis down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        ShortenResponse response = service.shorten("https://example.com/a");

        assertEquals("https://example.com/a", response.originalUrl());
    }

    // ---------- resolve ----------

    @Test
    void resolveReturnsCachedUrlWithoutTouchingDatabase() {
        when(valueOps.get("url:abc1234")).thenReturn("https://example.com/cached");

        String result = service.resolve("abc1234");

        assertEquals("https://example.com/cached", result);
        verifyNoInteractions(repository);
    }

    @Test
    void resolveOnCacheMissReadsDatabaseAndFillsCache() {
        when(valueOps.get("url:abc1234")).thenReturn(null);
        when(repository.findByShortCode("abc1234"))
                .thenReturn(Optional.of(new UrlMapping("abc1234", "https://example.com/from-db")));

        String result = service.resolve("abc1234");

        assertEquals("https://example.com/from-db", result);
        verify(valueOps).set("url:abc1234", "https://example.com/from-db", Duration.ofMinutes(60));
    }

    @Test
    void resolveUnknownCodeThrowsNotFound() {
        when(valueOps.get("url:zzzzzzz")).thenReturn(null);
        when(repository.findByShortCode("zzzzzzz")).thenReturn(Optional.empty());

        UrlNotFoundException ex = assertThrows(UrlNotFoundException.class, () -> service.resolve("zzzzzzz"));

        assertTrue(ex.getMessage().contains("zzzzzzz"));
    }

    @Test
    void resolveFallsBackToDatabaseWhenRedisIsDown() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        when(repository.findByShortCode("abc1234"))
                .thenReturn(Optional.of(new UrlMapping("abc1234", "https://example.com/from-db")));

        String result = service.resolve("abc1234");

        assertEquals("https://example.com/from-db", result);
    }
}
