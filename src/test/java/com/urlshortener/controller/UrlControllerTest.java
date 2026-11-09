package com.urlshortener.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.service.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UrlController.class)
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlService urlService;

    @Test
    void shortenReturns201WithShortUrl() throws Exception {
        when(urlService.shorten("https://example.com/a")).thenReturn(
                new ShortenResponse("aB3xYz9", "http://localhost:8080/aB3xYz9", "https://example.com/a"));

        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/a\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("aB3xYz9"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/aB3xYz9"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/a"));
    }

    @Test
    void shortenWithBlankUrlReturns400() throws Exception {
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("url must not be empty"));
    }

    @Test
    void shortenWithInvalidUrlReturns400() throws Exception {
        when(urlService.shorten("not-a-url")).thenThrow(new InvalidUrlException("URL is not valid"));

        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"not-a-url\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("URL is not valid"));
    }

    @Test
    void redirectReturns302WithLocationHeader() throws Exception {
        when(urlService.resolve("abc1234")).thenReturn("https://example.com/long/path");

        mockMvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/long/path"));
    }

    @Test
    void redirectForUnknownCodeReturns404() throws Exception {
        when(urlService.resolve("zzzzzzz")).thenThrow(new UrlNotFoundException("zzzzzzz"));

        mockMvc.perform(get("/zzzzzzz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
