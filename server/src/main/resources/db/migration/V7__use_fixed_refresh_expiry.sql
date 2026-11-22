-- Do not reactivate sessions that already reached their old idle deadline.
UPDATE user_sessions SET revoked = true WHERE sliding_expires_at <= CURRENT_TIMESTAMP;

ALTER TABLE user_sessions RENAME COLUMN absolute_expires_at TO expires_at;
ALTER TABLE user_sessions
    DROP COLUMN sliding_expires_at,
    DROP COLUMN previous_token_hash,
    DROP COLUMN previous_token_expires_at;
-- JWT timestamps use whole seconds; never round a deadline forward.
UPDATE user_sessions SET expires_at = date_trunc('second', expires_at);
CREATE INDEX user_sessions_expiration ON user_sessions (expires_at);

ALTER TABLE refresh_token_history DROP COLUMN grace_expires_at;
