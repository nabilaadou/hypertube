CREATE TABLE users (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider varchar(20) NOT NULL CHECK (provider IN ('42', 'google')),
    provider_user_id varchar(128) NOT NULL,
    display_name varchar(128),
    email varchar(254),
    UNIQUE (provider, provider_user_id)
);

CREATE TABLE oauth_login_handoffs (
    code uuid PRIMARY KEY,
    browser_proof_hash char(64) NOT NULL,
    provider varchar(20) NOT NULL CHECK (provider IN ('42', 'google')),
    provider_user_id varchar(128) NOT NULL,
    display_name varchar(128),
    email varchar(254),
    expires_at timestamptz NOT NULL DEFAULT (now() + interval '2 minutes')
);

CREATE INDEX oauth_login_handoffs_expiration ON oauth_login_handoffs (expires_at);
