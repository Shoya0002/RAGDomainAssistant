package com.vitassistant.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One retrievable unit: a slice of a source document plus the embedding
 * vector used to find it later. Embedding is stored as a comma-separated
 * string of floats to keep the scaffold dependency-free — see README for
 * how to swap this for a native pgvector column once things work end to end.
 */
@Entity
@Table(name = "document_chunk")
@Getter
@Setter
@NoArgsConstructor
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sourceFileName;

    @Lob
    @Column(nullable = false)
    private String content;

    // Comma-separated floats, e.g. "0.0123,-0.045,...". See EmbeddingService.
    @Lob
    @Column(nullable = false)
    private String embedding;

    @Column
    private String embeddingProfile;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public DocumentChunk(String sourceFileName, String content, String embedding, String embeddingProfile) {
        this.sourceFileName = sourceFileName;
        this.content = content;
        this.embedding = embedding;
        this.embeddingProfile = embeddingProfile;
    }
}
