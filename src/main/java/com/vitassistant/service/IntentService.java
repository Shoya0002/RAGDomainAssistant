package com.vitassistant.service;

import com.vitassistant.dto.IntentDataset;
import com.vitassistant.dto.IntentDefinition;
import com.vitassistant.model.entity.IntentQuestion;
import com.vitassistant.repository.IntentQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IntentService {

    private final EmbeddingService embeddingService;
    private final IntentQuestionRepository repository;

    @Transactional
    public int ingest(IntentDataset dataset) {
        if (dataset == null || dataset.intents() == null || dataset.intents().isEmpty()) {
            throw new IllegalArgumentException("Intent dataset must contain at least one intent");
        }

        String profile = embeddingService.embeddingProfile();
        List<IntentQuestion> questions = new ArrayList<>();
        for (IntentDefinition definition : dataset.intents()) {
            if (definition.questions() == null || definition.questions().isEmpty()) {
                throw new IllegalArgumentException("Each intent must contain at least one question");
            }
            for (String question : definition.questions()) {
                if (question == null || question.isBlank()) {
                    throw new IllegalArgumentException("Intent questions must not be blank");
                }
                float[] vector = embeddingService.embed(question);
                questions.add(new IntentQuestion(
                        definition.intent(),
                        definition.category(),
                        question,
                        embeddingService.toStorageFormat(vector),
                        profile
                ));
            }
        }

        repository.deleteAllInBatch();
        repository.saveAll(questions);
        return questions.size();
    }

    public Optional<String> classify(float[] queryEmbedding) {
        String profile = embeddingService.embeddingProfile();
        return repository.findByEmbeddingProfile(profile).stream()
                .max(Comparator.comparingDouble(question ->
                        cosineSimilarity(queryEmbedding, embeddingService.fromStorageFormat(question.getEmbedding()))
                ))
                .map(IntentQuestion::getCategory);
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || a.length == 0 || a.length != b.length) {
            throw new IllegalArgumentException("Query and intent embeddings must have the same non-empty dimensions");
        }

        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
