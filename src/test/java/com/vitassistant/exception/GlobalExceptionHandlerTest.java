package com.vitassistant.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    @Test
    void preservesSpringClientErrorStatus() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid request");

        var response = handler.handleGeneric(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, String> body = Objects.requireNonNull(response.getBody());
        assertNotNull(body.get("error"));
    }

    @Test
    void mapsProviderAuthenticationFailureToBadGateway() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        WebClientResponseException exception = providerException(401);

        var response = handler.handleGeneric(exception);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("AI provider rejected or failed the request (HTTP 401)",
                Objects.requireNonNull(response.getBody()).get("error"));
    }

    @Test
    void mapsProviderRateLimitToServiceUnavailable() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        WebClientResponseException exception = providerException(429);

        var response = handler.handleGeneric(exception);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("AI provider rate limit exceeded", Objects.requireNonNull(response.getBody()).get("error"));
    }

    private WebClientResponseException providerException(int status) {
        return new WebClientResponseException(
                status,
                "provider error",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
    }
}