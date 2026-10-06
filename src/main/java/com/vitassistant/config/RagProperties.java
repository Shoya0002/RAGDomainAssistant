package com.vitassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "rag")
@Getter
@Setter
public class RagProperties {
    private Llm llm = new Llm();
    private Embedding embedding = new Embedding();
    private Chunk chunk = new Chunk();
    private Retrieval retrieval = new Retrieval();

    @Getter @Setter
    public static class Llm {
        private String apiUrl;
        private String apiKey;
        private String model;
    }

    @Getter @Setter
    public static class Embedding {
        private String apiUrl;
        private String apiKey;
        private String model;
    }

    @Getter @Setter
    public static class Chunk {
        private int size;
        private int overlap;
    }

    @Getter @Setter
    public static class Retrieval {
        private int topK;
    }
}
