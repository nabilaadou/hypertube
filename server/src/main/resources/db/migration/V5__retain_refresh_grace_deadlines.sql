ALTER TABLE refresh_token_history ADD COLUMN grace_expires_at timestamptz NOT NULL DEFAULT '1970-01-01 00:00:00+00';
UPDATE refresh_token_history h SET grace_expires_at = s.previous_token_expires_at
FROM user_sessions s WHERE h.session_id = s.id AND h.token_hash = s.previous_token_hash;
ALTER TABLE refresh_token_history ALTER COLUMN grace_expires_at DROP DEFAULT;
