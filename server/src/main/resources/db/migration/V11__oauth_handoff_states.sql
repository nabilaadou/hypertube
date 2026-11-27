ALTER TABLE oauth_login_handoffs ADD COLUMN state varchar(24) NOT NULL DEFAULT 'INITIAL';
ALTER TABLE oauth_login_handoffs ADD COLUMN target_user_id bigint REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE oauth_login_handoffs ADD CONSTRAINT oauth_handoff_state CHECK (state IN ('INITIAL', 'PROFILE_REQUIRED', 'LINK_REQUIRED'));
ALTER TABLE oauth_login_handoffs ADD CONSTRAINT oauth_handoff_target CHECK (state <> 'LINK_REQUIRED' OR target_user_id IS NOT NULL);
