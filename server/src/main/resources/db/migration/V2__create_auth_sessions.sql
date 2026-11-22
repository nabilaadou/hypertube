CREATE TABLE auth_sessions (
    id uuid PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    access_token_hash char(64) NOT NULL UNIQUE,
    access_expires_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    CHECK (access_expires_at <= expires_at)
);

CREATE INDEX auth_sessions_user ON auth_sessions (user_id);
CREATE INDEX auth_sessions_expiration ON auth_sessions (expires_at);

CREATE TABLE auth_refresh_tokens (
    token_hash char(64) PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES auth_sessions (id) ON DELETE CASCADE,
    used_at timestamptz
);

CREATE INDEX auth_refresh_tokens_session ON auth_refresh_tokens (session_id);
