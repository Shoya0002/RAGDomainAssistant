package com.vitassistant.repository;

import com.vitassistant.model.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    List<DocumentChunk> findByEmbeddingProfile(String embeddingProfile);

    // VectorSearchService scores matching-profile chunks in memory. For larger indexes,
    // replace this with a native pgvector search method such as:
    // List<DocumentChunk> findNearest(String embeddingLiteral, int topK);
}
