package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShortenRequest(
        @NotBlank(message = "url must not be empty")
        @Size(max = 2048, message = "url must be at most 2048 characters")
        String url) {
}
