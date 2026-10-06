package com.vitassistant.service;

import com.vitassistant.config.RagProperties;
import com.vitassistant.dto.IntentDataset;
import com.vitassistant.dto.IntentDefinition;
import com.vitassistant.model.entity.IntentQuestion;
import com.vitassistant.repository.IntentQuestionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentServiceTest {

    @Test
    void indexesQuestionExamplesAndClassifiesByNearestVector() {
        RagProperties properties = new RagProperties();
        IntentQuestionRepository repository = mock(IntentQuestionRepository.class);
        EmbeddingService embeddingService = new EmbeddingService(WebClient.builder().build(), properties) {
            @Override
            public String embeddingProfile() {
                return "test-profile";
            }

            @Override
            public float[] embed(String text) {
                return text.startsWith("When") ? new float[]{1f, 0f} : new float[]{0f, 1f};
            }
        };
        IntentService service = new IntentService(embeddingService, repository);
        IntentDataset dataset = new IntentDataset(List.of(
                new IntentDefinition("academic_calendar", "Academic Calendar", List.of("When does the semester start?")),
                new IntentDefinition("fees", "Fees", List.of("How do I pay my fees?"))
        ));

        assertEquals(2, service.ingest(dataset));
        verify(repository).deleteAllInBatch();
        verify(repository).saveAll(anyList());

        when(repository.findByEmbeddingProfile("test-profile")).thenReturn(List.of(
                new IntentQuestion("academic_calendar", "Academic Calendar", "When does the semester start?", "1.0,0.0", "test-profile"),
                new IntentQuestion("fees", "Fees", "How do I pay my fees?", "0.0,1.0", "test-profile")
        ));
        assertEquals("Academic Calendar", service.classify(new float[]{0.9f, 0.1f}).orElseThrow());
    }

    @Test
    void leavesExistingTaxonomyUntouchedIfEmbeddingFails() {
        IntentQuestionRepository repository = mock(IntentQuestionRepository.class);
        EmbeddingService embeddingService = new EmbeddingService(WebClient.builder().build(), new RagProperties()) {
            @Override
            public String embeddingProfile() {
                return "test-profile";
            }

            @Override
            public float[] embed(String text) {
                throw new IllegalStateException("embedding provider unavailable");
            }
        };
        IntentService service = new IntentService(embeddingService, repository);
        IntentDataset dataset = new IntentDataset(List.of(
                new IntentDefinition("academic_calendar", "Academic Calendar", List.of("When does the semester start?"))
        ));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.ingest(dataset));
        assertEquals("embedding provider unavailable", exception.getMessage());
        verify(repository, org.mockito.Mockito.never()).deleteAllInBatch();
        verify(repository, org.mockito.Mockito.never()).saveAll(anyList());
    }
}
