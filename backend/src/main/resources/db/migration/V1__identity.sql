CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX users_email_uq ON users (lower(email));

CREATE TABLE user_profiles (
    user_id      UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    display_name VARCHAR(120) NOT NULL,
    timezone     VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    locale       VARCHAR(20)  NOT NULL DEFAULT 'en-IN',
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Refresh tokens are stored hashed. Tokens issued by rotation share a family_id so that
-- reuse of an already-rotated token revokes the whole family (stolen-token detection).
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id   UUID        NOT NULL,
    token_hash  CHAR(64)    NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    rotated_at  TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);

CREATE TABLE audit_events (
    id            BIGSERIAL PRIMARY KEY,
    actor_user_id UUID,
    action        VARCHAR(64)  NOT NULL,
    resource_type VARCHAR(64),
    resource_id   VARCHAR(64),
    metadata      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    ip            VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX audit_events_actor_idx ON audit_events (actor_user_id, created_at DESC);
