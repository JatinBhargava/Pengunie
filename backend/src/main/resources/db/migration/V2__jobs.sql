-- Postgres-backed job queue. Workers claim rows with FOR UPDATE SKIP LOCKED and hold a lease
-- (locked_until); a crashed worker's job becomes claimable again once the lease expires.
CREATE TABLE jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type            VARCHAR(64)  NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'QUEUED',
    attempts        INT          NOT NULL DEFAULT 0,
    max_attempts    INT          NOT NULL DEFAULT 5,
    run_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    locked_until    TIMESTAMPTZ,
    locked_by       VARCHAR(64),
    last_error      TEXT,
    idempotency_key VARCHAR(200) UNIQUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX jobs_claim_idx ON jobs (run_at) WHERE status IN ('QUEUED', 'RUNNING');
