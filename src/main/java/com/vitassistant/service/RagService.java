package com.vitassistant.service;

import com.vitassistant.dto.ChatResponse;
import com.vitassistant.model.entity.DocumentChunk;
import com.vitassistant.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The two entry points a controller needs: ingest() to add a document to the
 * knowledge base, and answer() to run a full retrieve-then-generate query.
 */
@Service
@RequiredArgsConstructor
public class RagService {

    private final PdfLoaderService pdfLoaderService;
    private final EmbeddingService embeddingService;
    private final IntentService intentService;
    private final VectorSearchService vectorSearchService;
    private final LlmService llmService;
    private final DocumentChunkRepository repository;

    public int ingest(MultipartFile file) throws IOException {
        String text = pdfLoaderService.extractText(file);
        List<String> chunks = pdfLoaderService.chunkText(text);
        if (chunks.isEmpty()) {
            throw new IOException("PDF contains no extractable text; scanned PDFs require OCR");
        }

        String embeddingProfile = embeddingService.embeddingProfile();
        List<DocumentChunk> entities = new ArrayList<>(chunks.size());
        for (String chunk : chunks) {
            float[] embedding = embeddingService.embed(chunk);
            entities.add(new DocumentChunk(
                    file.getOriginalFilename(),
                    chunk,
                    embeddingService.toStorageFormat(embedding),
                    embeddingProfile
            ));
        }

        repository.saveAll(entities);
        return chunks.size();
    }

    public ChatResponse answer(String question) {
        float[] queryEmbedding = embeddingService.embed(question);
        String intent = intentService.classify(queryEmbedding).orElse(null);
        List<DocumentChunk> relevant = vectorSearchService.findRelevantChunks(queryEmbedding);

        StringBuilder context = new StringBuilder();
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        for (DocumentChunk chunk : relevant) {
            if (context.length() > 0) {
                context.append("\n---\n");
            }
            context.append(chunk.getContent());
            sources.add(chunk.getSourceFileName());
        }

        String answer = llmService.generateAnswer(question, context.toString());

        return new ChatResponse(answer, List.copyOf(sources), intent);
    }
}
