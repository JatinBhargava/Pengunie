# ADR 0003 — Fixed embedding dimension per database

**Status:** accepted · 2026-10-02

## Context
pgvector columns have a fixed dimension, and HNSW indexes require one. Embedding models differ: OpenAI `text-embedding-3-small` produces 1536 dimensions, `nomic-embed-text` on Ollama produces 768.

## Decision
`chunks.embedding` is `vector(${embeddingDim})`. The Flyway placeholder comes from `app.embedding.dimensions` (default 1536, env `EMBEDDING_DIMENSIONS`) and is fixed when V3 runs. `EmbeddingService` rejects vectors of any other size, and each chunk records `embedding_model`.

## Consequences
- Switching to a model with a different dimension needs a new migration (alter the column, rebuild the index) plus a re-index job. Every document's text is kept in `document_pages`, so re-indexing never re-parses files.
- Switching between models with the same dimension still requires re-embedding, because vectors from different models are not comparable. `embedding_model` makes stale chunks easy to find.
- For local Ollama development, use a fresh database with `EMBEDDING_DIMENSIONS=768`.
