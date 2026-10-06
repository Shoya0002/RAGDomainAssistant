package com.vitassistant.service;

import com.vitassistant.model.entity.DocumentChunk;
import com.vitassistant.config.RagProperties;
import com.vitassistant.repository.DocumentChunkRepository;
import com.vitassistant.repository.IntentQuestionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RagServiceTest {

    @Test
    void ingestPersistsChunksOnlyAfterEveryEmbeddingSucceeds() throws IOException {
        Fixture fixture = fixture("document text", List.of("first", "second"), null);

        assertEquals(2, fixture.service().ingest(fixture.file()));

        assertEquals(1, fixture.repository().saveAllCalls);
        assertEquals(0, fixture.repository().saveCalls);
        assertEquals(2, fixture.repository().persisted.size());
        assertEquals("https://embedding.test|test-model", fixture.repository().persisted.get(0).getEmbeddingProfile());
    }

    @Test
    void ingestDoesNotPersistPartialChunksWhenEmbeddingFails() throws IOException {
        Fixture fixture = fixture(
                "document text",
                List.of("first", "second"),
                new IllegalStateException("provider unavailable")
        );

        assertThrows(IllegalStateException.class, () -> fixture.service().ingest(fixture.file()));

        assertEquals(0, fixture.repository().saveAllCalls);
        assertEquals(0, fixture.repository().saveCalls);
        assertEquals(0, fixture.repository().persisted.size());
    }

    @Test
    void ingestRejectsDocumentsWithoutExtractableText() throws IOException {
        Fixture fixture = fixture("   ", List.of(), null);

        assertThrows(IOException.class, () -> fixture.service().ingest(fixture.file()));

        assertEquals(0, fixture.repository().saveAllCalls);
        assertEquals(0, fixture.repository().persisted.size());
    }

    @Test
    void vectorSearchRejectsDocumentsIndexedWithAnotherEmbeddingProfile() {
        RagProperties properties = new RagProperties();
        properties.getRetrieval().setTopK(2);
        RecordingRepository recordingRepository = new RecordingRepository();
        recordingRepository.persisted.add(new DocumentChunk("old.pdf", "old content", "0.5", "old-profile"));
        DocumentChunkRepository repository = recordingRepository.proxy();
        EmbeddingService embeddingService = new EmbeddingService(WebClient.builder().build(), properties) {
            @Override
            public String embeddingProfile() {
                return "current-profile";
            }

            @Override
            public float[] fromStorageFormat(String stored) {
                return new float[]{0.5f};
            }
        };
        VectorSearchService vectorSearchService = new VectorSearchService(repository, embeddingService, properties);

        assertThrows(
                IllegalStateException.class,
                () -> vectorSearchService.findRelevantChunks(new float[]{0.5f})
        );
    }

    private Fixture fixture(String text, List<String> chunks, RuntimeException embeddingFailure) {
        RagProperties properties = new RagProperties();
        RecordingRepository recordingRepository = new RecordingRepository();
        DocumentChunkRepository repository = recordingRepository.proxy();
        MultipartFile file = new MockMultipartFile("file", "handbook.pdf", "application/pdf", new byte[]{1});

        PdfLoaderService pdfLoaderService = new PdfLoaderService(properties) {
            @Override
            public String extractText(MultipartFile file) {
                return text;
            }

            @Override
            public List<String> chunkText(String ignored) {
                return chunks;
            }
        };

        EmbeddingService embeddingService = new EmbeddingService(WebClient.builder().build(), properties) {
            @Override
            public String embeddingProfile() {
                return "https://embedding.test|test-model";
            }

            @Override
            public float[] embed(String chunk) {
                if ("second".equals(chunk) && embeddingFailure != null) {
                    throw embeddingFailure;
                }
                return new float[]{0.5f};
            }

            @Override
            public String toStorageFormat(float[] embedding) {
                return "0.5";
            }
        };

        RagService service = new RagService(
                pdfLoaderService,
                embeddingService,
                new IntentService(embeddingService, mock(IntentQuestionRepository.class)),
                new VectorSearchService(repository, embeddingService, properties),
                new LlmService(WebClient.builder().build(), properties),
                repository
        );
        return new Fixture(service, file, recordingRepository);
    }

    private record Fixture(RagService service, MultipartFile file, RecordingRepository repository) {
    }

    private static class RecordingRepository implements InvocationHandler {
        private final List<DocumentChunk> persisted = new ArrayList<>();
        private int saveAllCalls;
        private int saveCalls;

        private DocumentChunkRepository proxy() {
            return (DocumentChunkRepository) Proxy.newProxyInstance(
                    DocumentChunkRepository.class.getClassLoader(),
                    new Class<?>[]{DocumentChunkRepository.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "saveAll" -> {
                    saveAllCalls++;
                    for (Object entity : (Iterable<?>) arguments[0]) {
                        persisted.add((DocumentChunk) entity);
                    }
                    yield List.copyOf(persisted);
                }
                case "save" -> {
                    saveCalls++;
                    persisted.add((DocumentChunk) arguments[0]);
                    yield arguments[0];
                }
                case "findByEmbeddingProfile" -> persisted.stream()
                        .filter(chunk -> arguments[0].equals(chunk.getEmbeddingProfile()))
                        .toList();
                case "count" -> (long) persisted.size();
                case "toString" -> "RecordingDocumentChunkRepository";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }
}