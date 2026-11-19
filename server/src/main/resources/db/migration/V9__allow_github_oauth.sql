ALTER TABLE users
    DROP CONSTRAINT users_provider_check,
    ADD CONSTRAINT users_provider_check CHECK (provider IN ('42', 'google', 'github'));

ALTER TABLE oauth_login_handoffs
    DROP CONSTRAINT oauth_login_handoffs_provider_check,
    ADD CONSTRAINT oauth_login_handoffs_provider_check CHECK (provider IN ('42', 'google', 'github'));
