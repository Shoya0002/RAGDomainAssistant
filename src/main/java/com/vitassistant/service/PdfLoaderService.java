package com.vitassistant.service;

import com.vitassistant.config.RagProperties;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a PDF into a list of overlapping text chunks small enough to embed
 * and retrieve individually. Chunk size/overlap come from application.yml
 * (rag.chunk.size / rag.chunk.overlap) so you can tune them without a redeploy.
 */
@Service
@RequiredArgsConstructor
public class PdfLoaderService {

    private final RagProperties ragProperties;

    public String extractText(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    public List<String> chunkText(String text) {
        int size = ragProperties.getChunk().getSize();
        int overlap = ragProperties.getChunk().getOverlap();
        if (size <= 0 || overlap < 0 || overlap >= size) {
            throw new IllegalStateException(
                    "rag.chunk.size must be positive and rag.chunk.overlap must be between 0 and size - 1"
            );
        }

        List<String> chunks = new ArrayList<>();

        String normalized = text.replaceAll("\\s+", " ").trim();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + size, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) break;
            start = end - overlap; // step back so chunks overlap for context continuity
        }
        return chunks;
    }
}
