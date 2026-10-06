# VIT RAG Assistant — Scaffold

Matches your diagram: Java Spring Boot backend, PostgreSQL, PDF loader,
vector search, LLM API — wired end to end but with real logic left simple
on purpose so you can see how every piece works before you optimize any of it.

## What's already built

```
com.vitassistant
├── VitAssistantApplication   — entry point
├── config/
│   ├── RagProperties         — typed binding for the rag.* settings in application.yml
│   └── WebClientConfig       — HTTP client bean used to call embedding/LLM APIs
├── controller/
│   └── ChatController        — POST /api/documents (upload PDF), POST /api/chat (ask)
├── service/
│   ├── PdfLoaderService       — extracts text from PDFs, splits into overlapping chunks
│   ├── EmbeddingService       — turns text into vectors via an embedding API
│   ├── VectorSearchService    — cosine-similarity search over stored chunks
│   ├── LlmService             — sends question + retrieved context to the LLM
│   └── RagService             — glues the above into ingest() and answer()
├── repository/
│   └── DocumentChunkRepository
├── model/entity/
│   └── DocumentChunk          — one text chunk + its embedding
└── exception/
    └── GlobalExceptionHandler
```

## Prerequisites

- JDK 17+
- Maven (or use the `mvnw` wrapper if you add one via `mvn -N wrapper:wrapper`)
- Docker (for Postgres) — or a local Postgres install with the `pgvector` extension
- An API key for an embedding + chat model (OpenAI-compatible; swap providers by editing `application.yml` and the two service classes)

## Step 1 — Start the database

```bash
docker compose up -d
```

This starts Postgres with the `pgvector` extension enabled (via `init.sql`).
It's not actually used for vector search yet in this scaffold — see "Scaling
retrieval" below — but it's there and ready for when you need it.

## Step 2 — Set your API key

```bash
export LLM_API_KEY=sk-...
```

`application.yml` reads this into both `rag.llm.api-key` and
`rag.embedding.api-key`. If you're using different providers for embeddings
vs chat, split them into two env vars and update `application.yml`.

## Step 3 — Build and run

```bash
mvn spring-boot:run
```

Spring Boot will auto-create the `document_chunk` table on startup
(`ddl-auto: update`). App comes up on `http://localhost:8080`.

## Step 4 — Ingest a document

```bash
curl -X POST http://localhost:8080/api/documents \
  -F "file=@/path/to/academic-calendar.pdf"
```

This extracts the text, splits it into ~800-character overlapping chunks,
embeds each chunk, and stores it.

## Step 5 — Index intent questions

In the frontend, choose `src/main/resources/intents.json` and select **Index
JSON**. Each example question is embedded and stored for nearest-vector intent
classification. These examples contain no answers, so they classify questions
but do not supply factual answer content. Facts still come from uploaded PDFs.

## Step 6 — Ask a question

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "When does the odd semester end?"}'
```

The service embeds your question, identifies the closest configured intent
when one has been indexed, finds the most similar PDF chunks, and returns the
answer, matched intent, and source files.

## How retrieval works right now (and how to scale it)

`VectorSearchService` currently loads **every** stored chunk and computes
cosine similarity in Java. That's intentionally simple so you can read and
trust the whole pipeline first. It's fine for a few thousand chunks — plenty
for one college's worth of PDFs to start.

When you outgrow that:
1. Change `DocumentChunk.embedding` to a real `vector(1536)` column (pgvector
   is already installed via `init.sql`) instead of a comma-separated string.
2. Add a native query in `DocumentChunkRepository`, e.g.
   `SELECT * FROM document_chunk ORDER BY embedding <=> :queryVector LIMIT :k`
   (the `<=>` operator is pgvector's cosine-distance operator).
3. Delete the in-memory loop in `VectorSearchService` and call that query
   instead.

This lets Postgres do the nearest-neighbor search with an index, instead of
the JVM scanning every row.

## Suggested next steps, in order

1. Get one PDF ingested and one question answered end to end — confirms the
   whole pipeline works before you add anything else.
2. Add a `DELETE /api/documents/{fileName}` endpoint so you can re-ingest
   without wiping the DB by hand.
3. Add chat history so `answer()` can handle follow-up questions, not just
   one-shot ones.
4. Swap in real admin data (timetables, fee deadlines) as PDFs or scraped
   pages — this is what makes it "your college's" assistant rather than a
   generic PDF Q&A tool.
5. Move retrieval to native pgvector once you're past a few thousand chunks.
6. Add basic auth or rate limiting before you expose this beyond yourself.
   