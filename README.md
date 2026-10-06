# Personal Intelligence OS

A privacy-first personal knowledge platform: upload your documents, search them, and ask questions that are answered **only** from your own files, with citations verified down to the page.

This repository covers weeks 1–4 of the 12-week PRD: **Foundation → Documents → Search → Cited RAG chat**.

| | |
|---|---|
| Backend | Java 21 · Spring Boot 4.1 · Spring AI 2.0 · PostgreSQL 17 + pgvector · Flyway |
| Frontend | React 19 · TypeScript · Vite · TanStack Query · Tailwind 4 |
| AI providers | OpenAI, Anthropic (chat) + OpenAI embeddings, Ollama, or deterministic fakes, chosen by Spring profile |

## Quick start

Prerequisites: Java 21, Node 24 + pnpm, Docker.

```bash
# 1. Postgres + pgvector (set PIOS_DB_PORT=5433 if 5432 is taken, and put DB_URL in backend/.env.local)
docker compose -f infrastructure/docker-compose.yml up -d

# 2. Stable JWT signing keys -> backend/.env.local (git-ignored)
./scripts/gen-dev-keys.sh

# 3. API on :8080. Default profile "fake" needs no API key.
./scripts/dev-backend.sh            # or: openai | anthropic | ollama

# 4. Web app on :5173 (proxies /api to :8080)
cd frontend && pnpm install && pnpm dev
```

For real models, add `OPENAI_API_KEY` (and `ANTHROPIC_API_KEY` for the `anthropic` profile) to `backend/.env.local`; see `backend/.env.example`. API docs: http://localhost:8080/api/docs.

## How it works

```
upload ─► validate (Tika sniffing, 25 MB, sha256 dedupe) ─► storage ─► jobs table
             PARSE_DOCUMENT: PDFBox per-page / Tika ─► document_pages
             INDEX_DOCUMENT: page-aware chunker ─► embeddings ─► chunks (pgvector HNSW + tsvector GIN)

question ─► embed ─► scoped retrieval (owner filter in SQL) ─► below threshold? "not found" (no LLM call)
         ─► prompt with <source id=n> delimited, untrusted excerpts ─► structured answer {answerable, answer, citations}
         ─► CitationValidator (source must be retrieved, quote must occur in it) ─► GROUNDED / NOT_FOUND / UNVERIFIED
         ─► citation narrowed to the exact page containing the quote
```

Design rules enforced in code:
- **Authorization before retrieval.** Every retrieval method takes an `AccessScope` and filters in SQL. Tests assert one user's data never reaches another user (search, chat and file links).
- **Uploaded content is untrusted.** Sources are delimited, and tag-like text inside them is neutralized.
- **Grounding is checked deterministically.** Unverifiable answers are withheld, not shown.
- **Cost tracking from day one.** Every model call writes an `llm_calls` row with purpose, model, tokens and latency.

Decisions are recorded in [`docs/adr`](docs/adr): Postgres job queue, self-issued JWT (and the Keycloak migration path), embedding dimensions, filesystem vs. S3 storage.

## API (v1)

| Method | Path | |
|---|---|---|
| POST | `/api/v1/auth/register` · `/login` · `/refresh` · `/logout` | access JWT in body, refresh token in httpOnly cookie |
| GET/PUT | `/api/v1/me` | profile, timezone, locale |
| POST/GET | `/api/v1/documents` | upload (multipart) / list (`?collectionId=`) |
| GET/PATCH/DELETE | `/api/v1/documents/{id}` | status, move to collection, delete |
| GET | `/api/v1/documents/{id}/file` · `/chunks` | short-lived file link · indexed chunks |
| GET/POST/DELETE | `/api/v1/collections` | |
| POST | `/api/v1/search` | `{query, mode: SEMANTIC\|KEYWORD, collectionId?, topK?}` |
| POST | `/api/v1/chat` | `{message, conversationId?, collectionId?}` |
| GET/DELETE | `/api/v1/conversations[/{id}]` | |
| GET | `/api/v1/audit/events` | your audit trail |

## Testing

```bash
cd backend && ./mvnw verify      # unit tests, then Testcontainers ITs against pgvector, plus the RAG eval
cd frontend && pnpm lint && pnpm typecheck && pnpm test && pnpm build
```

### RAG evaluation

`evals/rag/seed.jsonl` holds curated cases (answerable, unanswerable, and *denied*: the answer exists only in another user's documents) over the documents in `evals/rag/docs`. `RagEvalIT` reports retrieval hit rate, citation validity, decline rates and leakage, and writes `backend/target/rag-eval-report.json`. Leakage must be zero in every run.

```bash
# Against a real provider, enforcing the PRD targets (≥85% hit rate, ≥90% valid citations):
OPENAI_API_KEY=... ./mvnw verify -Dit.test=RagEvalIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false \
  -Deval.profile=openai -Deval.enforce=true
```

With the default fake models the numbers only show that the harness works; they say nothing about answer quality.

## Next (per PRD)

Week 5: hybrid search (RRF over the existing semantic + keyword queries), reranking, query rewrite, grow evals to 75–150 cases. Week 6: family workspaces (`AccessScope` gains workspace ids). Week 7: memory + event extraction. Week 8: reminders and notifications on the job queue.
