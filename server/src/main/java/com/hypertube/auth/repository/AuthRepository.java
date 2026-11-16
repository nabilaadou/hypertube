package com.hypertube.auth.repository;

import java.util.Optional;
import java.util.UUID;

import com.hypertube.auth.model.OAuthIdentity;
import com.hypertube.auth.model.UserProfile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AuthRepository {
    private final JdbcClient jdbc;

    AuthRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public UUID startLogin(OAuthIdentity identity) {
        jdbc.sql("DELETE FROM oauth_login_handoffs WHERE expires_at <= now()").update();
        return jdbc.sql("""
                INSERT INTO oauth_login_handoffs (code, browser_proof_hash, provider, provider_user_id, display_name, email)
                VALUES (gen_random_uuid(), :proofHash, :provider, :providerUserId, :displayName, :email)
                RETURNING code
                """).paramSource(identity).query(UUID.class).single();
    }

    public Optional<Long> finishLogin(UUID code, String proofHash) {
        return jdbc.sql("""
                WITH identity AS (
                    DELETE FROM oauth_login_handoffs
                    WHERE code = :code AND browser_proof_hash = :proofHash AND expires_at > now()
                    RETURNING provider, provider_user_id, display_name, email
                )
                INSERT INTO users (provider, provider_user_id, display_name, email)
                SELECT provider, provider_user_id, display_name, email FROM identity
                ON CONFLICT (provider, provider_user_id) DO UPDATE
                SET display_name = COALESCE(EXCLUDED.display_name, users.display_name),
                    email = COALESCE(EXCLUDED.email, users.email)
                RETURNING id
                """).param("code", code).param("proofHash", proofHash).query(Long.class).optional();
    }

    public Optional<UserProfile> findProfile(long id) {
        return jdbc.sql("SELECT id, provider, provider_user_id, display_name, email FROM users WHERE id = :id")
                .param("id", id).query(UserProfile.class).optional();
    }
}
