package com.vitassistant.service;

import com.vitassistant.config.RagProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Objects;

/**
 * Sends the user's question plus retrieved context to a chat completion API.
 * Default config points at OpenAI's chat/completions endpoint — swap for
 * Anthropic, a local model server, or anything else that speaks a similar
 * JSON shape; just adjust buildRequestBody/extractAnswer to match.
 */
@Service
@RequiredArgsConstructor
public class LlmService {

    private final WebClient webClient;
    private final RagProperties ragProperties;

    private static final String SYSTEM_PROMPT = """
            You are the VIT college assistant. Answer the student's question
            using ONLY the context provided below. If the context doesn't
            contain the answer, say you don't have that information rather
            than guessing.
            """;

    public String generateAnswer(String question, String context) {
        String model = Objects.requireNonNull(requireConfiguration(ragProperties.getLlm().getModel(), "rag.llm.model"));
        String apiUrl = Objects.requireNonNull(requireConfiguration(ragProperties.getLlm().getApiUrl(), "rag.llm.api-url"));
        String apiKey = Objects.requireNonNull(requireConfiguration(ragProperties.getLlm().getApiKey(), "rag.llm.api-key"));
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", java.util.List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", "Context:\n" + context + "\n\nQuestion: " + question)
                )
        );
                Object requestBody = Objects.requireNonNull(body);

                JsonNode response = webClient.post()
                .uri(apiUrl)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                                .bodyToMono(JsonNode.class)
                .block();

                JsonNode choices = response == null ? null : response.path("choices");
                if (choices == null || !choices.isArray() || choices.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "LLM provider returned no completion choices");
                }

                JsonNode content = choices.get(0).path("message").path("content");
                if (!content.isTextual() || content.asText().isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "LLM provider returned an empty or invalid answer");
                }
                return content.asText();
        }

        private String requireConfiguration(String value, String property) {
                if (value == null || value.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, property + " must be configured");
                }
                return value.trim();
    }
}
