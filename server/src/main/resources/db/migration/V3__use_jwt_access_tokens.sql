ALTER TABLE auth_sessions RENAME TO auth_refresh_grants;
ALTER TABLE auth_refresh_grants ADD COLUMN access_token_id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE auth_refresh_grants ALTER COLUMN access_token_id DROP DEFAULT;
ALTER TABLE auth_refresh_grants DROP COLUMN access_token_hash;
ALTER TABLE auth_refresh_grants RENAME CONSTRAINT auth_sessions_pkey TO auth_refresh_grants_pkey;
ALTER TABLE auth_refresh_grants RENAME CONSTRAINT auth_sessions_user_id_fkey TO auth_refresh_grants_user_id_fkey;
ALTER TABLE auth_refresh_grants RENAME CONSTRAINT auth_sessions_check TO auth_refresh_grants_check;
ALTER INDEX auth_sessions_user RENAME TO auth_refresh_grants_user;
ALTER INDEX auth_sessions_expiration RENAME TO auth_refresh_grants_expiration;

ALTER TABLE auth_refresh_tokens RENAME COLUMN session_id TO grant_id;
ALTER TABLE auth_refresh_tokens RENAME CONSTRAINT auth_refresh_tokens_session_id_fkey TO auth_refresh_tokens_grant_id_fkey;
ALTER INDEX auth_refresh_tokens_session RENAME TO auth_refresh_tokens_grant;

CREATE TABLE auth_revoked_access_tokens (
    token_id uuid PRIMARY KEY,
    expires_at timestamptz NOT NULL
);

CREATE INDEX auth_revoked_access_tokens_expiration ON auth_revoked_access_tokens (expires_at);
