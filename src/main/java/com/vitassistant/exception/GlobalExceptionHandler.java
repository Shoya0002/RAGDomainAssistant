package com.vitassistant.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        if (ex instanceof WebClientResponseException providerException) {
            boolean rateLimited = providerException.getStatusCode().value() == 429;
            HttpStatus status = rateLimited ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
            String message = rateLimited
                    ? "AI provider rate limit exceeded"
                    : "AI provider rejected or failed the request (HTTP "
                            + providerException.getStatusCode().value() + ")";
            return ResponseEntity.status(status).body(Map.of("error", message));
        }

        if (ex instanceof WebClientException) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Could not connect to the AI provider"));
        }

        if (ex instanceof ErrorResponse errorResponse) {
            String detail = errorResponse.getBody().getDetail();
            String error = detail == null || detail.isBlank() ? "Request could not be processed" : detail;
            return ResponseEntity.status(errorResponse.getStatusCode()).body(Map.of("error", error));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", ex.getMessage() == null ? "Unexpected error" : ex.getMessage()));
    }
}
