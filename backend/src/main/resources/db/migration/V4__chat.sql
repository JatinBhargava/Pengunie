CREATE TABLE llm_calls (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID,
    purpose         VARCHAR(40)  NOT NULL,
    provider_model  VARCHAR(120),
    input_tokens    INT,
    output_tokens   INT,
    latency_ms      INT          NOT NULL,
    success         BOOLEAN      NOT NULL,
    error           TEXT,
    reference_type  VARCHAR(40),
    reference_id    UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX llm_calls_user_idx ON llm_calls (user_id, created_at DESC);

CREATE TABLE conversations (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title         VARCHAR(200) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX conversations_owner_idx ON conversations (owner_user_id, updated_at DESC);

CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role            VARCHAR(16) NOT NULL,
    content         TEXT        NOT NULL,
    grounding       VARCHAR(20),
    citations       JSONB       NOT NULL DEFAULT '[]'::jsonb,
    retrieval       JSONB       NOT NULL DEFAULT '[]'::jsonb,
    llm_call_id     UUID REFERENCES llm_calls (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX messages_conversation_idx ON messages (conversation_id, created_at);
