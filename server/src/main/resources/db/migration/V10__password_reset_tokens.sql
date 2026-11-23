CREATE TABLE password_reset_tokens (
    user_id bigint PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    token_hash char(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL
);
CREATE INDEX password_reset_tokens_expiration ON password_reset_tokens (expires_at);
