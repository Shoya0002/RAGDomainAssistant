package com.vitassistant.service;

import com.vitassistant.config.RagProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Wraps whatever embedding API you configure in application.yml
 * (rag.embedding.*). Default config points at OpenAI's embeddings endpoint —
 * swap the URL/model for any OpenAI-compatible provider.
 */
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final WebClient webClient;
    private final RagProperties ragProperties;

    public float[] embed(String text) {
        String model = Objects.requireNonNull(
            requireConfiguration(ragProperties.getEmbedding().getModel(), "rag.embedding.model")
        );
        String apiUrl = Objects.requireNonNull(
            requireConfiguration(ragProperties.getEmbedding().getApiUrl(), "rag.embedding.api-url")
        );
        String apiKey = Objects.requireNonNull(
            requireConfiguration(ragProperties.getEmbedding().getApiKey(), "rag.embedding.api-key")
        );
        Map<String, Object> body = Map.of(
                "model", model,
                "input", text
        );
        Object requestBody = Objects.requireNonNull(body);

        JsonNode response = webClient.post()
                .uri(apiUrl)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        JsonNode data = response == null ? null : response.path("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding provider returned no embedding data");
        }

        JsonNode vector = data.get(0).path("embedding");
        if (!vector.isArray() || vector.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding provider returned an invalid vector");
        }

        float[] result = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            JsonNode value = vector.get(i);
            if (!value.isNumber()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding provider returned a non-numeric vector value");
            }
            result[i] = value.floatValue();
            if (!Float.isFinite(result[i])) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding provider returned a non-finite vector value");
            }
        }
        return result;
    }

    public String embeddingProfile() {
        String apiUrl = requireConfiguration(ragProperties.getEmbedding().getApiUrl(), "rag.embedding.api-url");
        String model = requireConfiguration(ragProperties.getEmbedding().getModel(), "rag.embedding.model");
        return apiUrl.trim() + "|" + model.trim();
    }

    private String requireConfiguration(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, property + " must be configured");
        }
        return value.trim();
    }

    public String toStorageFormat(float[] embedding) {
        return java.util.stream.IntStream.range(0, embedding.length)
                .mapToObj(i -> String.valueOf(embedding[i]))
                .collect(Collectors.joining(","));
    }

    public float[] fromStorageFormat(String stored) {
        String[] parts = stored.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = Float.parseFloat(parts[i]);
        return result;
    }
}
