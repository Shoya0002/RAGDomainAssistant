package com.vitassistant.service;

import com.vitassistant.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderResponseValidationTest {

    @Test
    void embeddingServiceRejectsResponseWithoutEmbeddingData() {
        RagProperties properties = new RagProperties();
        properties.getEmbedding().setApiUrl("https://embedding.test");
        properties.getEmbedding().setApiKey("test-key");
        properties.getEmbedding().setModel("test-model");
        EmbeddingService service = new EmbeddingService(webClientReturning("{\"data\":[]}"), properties);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.embed("question")
        );
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
    }

    @Test
    void llmServiceRejectsResponseWithoutCompletionChoices() {
        RagProperties properties = new RagProperties();
        properties.getLlm().setApiUrl("https://llm.test");
        properties.getLlm().setApiKey("test-key");
        properties.getLlm().setModel("test-model");
        LlmService service = new LlmService(webClientReturning("{\"choices\":[]}"), properties);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.generateAnswer("question", "context")
        );
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
    }

    private WebClient webClientReturning(String json) {
        return WebClient.builder()
                .exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .body(Objects.requireNonNull(json))
                                .build()
                ))
                .build();
    }
}