CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE collections (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name          VARCHAR(120) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX collections_owner_name_uq ON collections (owner_user_id, lower(name));

CREATE TABLE documents (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    collection_id UUID REFERENCES collections (id) ON DELETE SET NULL,
    title         VARCHAR(300) NOT NULL,
    filename      VARCHAR(300) NOT NULL,
    mime_type     VARCHAR(120) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    sha256        CHAR(64)     NOT NULL,
    storage_key   VARCHAR(400) NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'UPLOADED',
    page_count    INT,
    error         TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ
);
CREATE UNIQUE INDEX documents_owner_sha_uq ON documents (owner_user_id, sha256) WHERE deleted_at IS NULL;
CREATE INDEX documents_owner_idx ON documents (owner_user_id, created_at DESC);

-- Extracted text, one row per page (or one row for unpaged formats). Chunking reads from here
-- so re-chunking / re-embedding never needs to re-parse the original file.
CREATE TABLE document_pages (
    document_id UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    page_number INT  NOT NULL,
    content     TEXT NOT NULL,
    PRIMARY KEY (document_id, page_number)
);

-- owner_user_id is denormalised so that every retrieval query can filter on the access scope
-- without a join. The embedding dimension is fixed at migration time (see ADR 0003).
CREATE TABLE chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    owner_user_id   UUID        NOT NULL,
    ordinal         INT         NOT NULL,
    page_start      INT         NOT NULL,
    page_end        INT         NOT NULL,
    heading         VARCHAR(300),
    content         TEXT        NOT NULL,
    token_count     INT         NOT NULL,
    tsv             TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', coalesce(heading, '') || ' ' || content)) STORED,
    embedding       VECTOR(${embeddingDim}),
    embedding_model VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, ordinal)
);
CREATE INDEX chunks_owner_doc_idx ON chunks (owner_user_id, document_id);
CREATE INDEX chunks_tsv_idx ON chunks USING gin (tsv);
CREATE INDEX chunks_embedding_idx ON chunks USING hnsw (embedding vector_cosine_ops);
