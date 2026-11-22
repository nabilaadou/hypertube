CREATE TABLE user_sessions (
    id uuid PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    refresh_token_hash char(64) NOT NULL UNIQUE,
    previous_token_hash char(64),
    previous_token_expires_at timestamptz,
    device_info varchar(255) NOT NULL,
    ip_address varchar(45) NOT NULL,
    created_at timestamptz NOT NULL,
    last_used_at timestamptz NOT NULL,
    sliding_expires_at timestamptz NOT NULL,
    absolute_expires_at timestamptz NOT NULL,
    revoked boolean NOT NULL DEFAULT false,
    CHECK (sliding_expires_at <= absolute_expires_at),
    CHECK (created_at <= last_used_at AND created_at <= absolute_expires_at),
    CHECK ((previous_token_hash IS NULL) = (previous_token_expires_at IS NULL))
);
CREATE INDEX user_sessions_user ON user_sessions (user_id);
CREATE INDEX user_sessions_previous_token ON user_sessions (previous_token_hash) WHERE previous_token_hash IS NOT NULL;
CREATE INDEX user_sessions_expiration ON user_sessions (sliding_expires_at);
CREATE INDEX user_sessions_revoked ON user_sessions (id) WHERE revoked;

CREATE TABLE refresh_token_history (
    token_hash char(64) PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES user_sessions (id) ON DELETE CASCADE
);
CREATE INDEX refresh_token_history_session ON refresh_token_history (session_id);

-- Legacy grants lack device metadata and creation times; preserve their existing absolute deadline.
INSERT INTO user_sessions (id, user_id, refresh_token_hash, previous_token_hash, previous_token_expires_at,
    device_info, ip_address, created_at, last_used_at, sliding_expires_at, absolute_expires_at, revoked)
SELECT g.id, g.user_id, current_token.token_hash, previous_token.token_hash,
    previous_token.used_at + interval '5 seconds', 'Unknown device (migrated)', 'unknown',
    LEAST(now(), g.expires_at - interval '30 days', previous_token.used_at),
    COALESCE(previous_token.used_at, LEAST(now(), g.expires_at - interval '30 days')),
    LEAST(now() + interval '7 days', g.expires_at),
    LEAST(now() + interval '30 days', g.expires_at), g.revoked_at IS NOT NULL
FROM auth_refresh_grants g
JOIN auth_refresh_tokens current_token ON current_token.grant_id = g.id AND current_token.used_at IS NULL
LEFT JOIN LATERAL (
    SELECT t.token_hash, t.used_at FROM auth_refresh_tokens t
    WHERE t.grant_id = g.id AND t.used_at IS NOT NULL ORDER BY t.used_at DESC, t.token_hash LIMIT 1
) previous_token ON true;

INSERT INTO refresh_token_history (token_hash, session_id)
SELECT t.token_hash, t.grant_id FROM auth_refresh_tokens t
JOIN user_sessions s ON s.id = t.grant_id WHERE t.used_at IS NOT NULL;

CREATE TABLE revoked_sessions (
    session_id uuid PRIMARY KEY,
    expires_at timestamptz NOT NULL
);
CREATE INDEX revoked_sessions_expiration ON revoked_sessions (expires_at);

DROP TABLE auth_refresh_tokens;
DROP TABLE auth_refresh_grants;
DROP TABLE auth_revoked_access_tokens;
