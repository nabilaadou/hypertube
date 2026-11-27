CREATE TABLE email_change_tokens (
    user_id bigint PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    email varchar(254) NOT NULL,
    token_hash char(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL
);
CREATE TABLE profile_reauth_attempts (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL UNIQUE REFERENCES user_sessions(id) ON DELETE CASCADE,
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    browser_hash char(64) NOT NULL,
    provider varchar(20) NOT NULL,
    provider_user_id varchar(128) NOT NULL,
    started_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    verified boolean NOT NULL DEFAULT false
);
