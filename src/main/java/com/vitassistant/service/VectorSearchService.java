package com.vitassistant.service;

import com.vitassistant.config.RagProperties;
import com.vitassistant.model.entity.DocumentChunk;
import com.vitassistant.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Simplest possible retrieval: load every chunk, score it against the query
 * embedding with cosine similarity, take the top K. Fine up to a few thousand
 * chunks. Once your document set grows past that, replace this with a native
 * pgvector query (see README "Scaling retrieval") so Postgres does the
 * nearest-neighbor search instead of the JVM.
 */
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final DocumentChunkRepository repository;
    private final EmbeddingService embeddingService;
    private final RagProperties ragProperties;

    public List<DocumentChunk> findRelevantChunks(float[] queryEmbedding) {
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            throw new IllegalArgumentException("Query embedding must not be null or empty");
        }

        int topK = ragProperties.getRetrieval().getTopK();
        if (topK <= 0) {
            throw new IllegalStateException("rag.retrieval.top-k must be greater than zero");
        }

        String embeddingProfile = embeddingService.embeddingProfile();
        List<DocumentChunk> all = repository.findByEmbeddingProfile(embeddingProfile);
        if (all.isEmpty() && repository.count() > 0) {
            throw new IllegalStateException(
                    "No indexed documents match the configured embedding model; re-ingest the documents"
            );
        }

        return all.stream()
                .sorted(Comparator.comparingDouble(
                        (DocumentChunk c) -> -cosineSimilarity(queryEmbedding, embeddingService.fromStorageFormat(c.getEmbedding()))
                ))
                .limit(topK)
                .toList();
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Query and stored embeddings must have the same dimensions");
        }

        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
