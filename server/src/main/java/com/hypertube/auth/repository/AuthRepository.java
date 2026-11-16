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
        UUID code = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO oauth_login_handoffs (code, browser_proof_hash, provider, provider_user_id, display_name, email)
                VALUES (?, ?, ?, ?, ?, ?)
                """).params(code, identity.proofHash(), identity.provider(), identity.providerUserId(),
                identity.displayName(), identity.email()).update();
        return code;
    }

    public Optional<Long> finishLogin(UUID code, String proofHash) {
        return jdbc.sql("""
                WITH identity AS (
                    DELETE FROM oauth_login_handoffs
                    WHERE code = ? AND browser_proof_hash = ? AND expires_at > now()
                    RETURNING provider, provider_user_id, display_name, email
                )
                INSERT INTO users (provider, provider_user_id, display_name, email)
                SELECT provider, provider_user_id, display_name, email FROM identity
                ON CONFLICT (provider, provider_user_id) DO UPDATE
                SET display_name = COALESCE(EXCLUDED.display_name, users.display_name),
                    email = COALESCE(EXCLUDED.email, users.email)
                RETURNING id
                """).params(code, proofHash).query(Long.class).optional();
    }

    public Optional<UserProfile> findProfile(long id) {
        return jdbc.sql("SELECT id, provider, provider_user_id, display_name, email FROM users WHERE id = ?")
                .param(id).query((row, index) -> new UserProfile(row.getLong("id"), row.getString("provider"),
                        row.getString("provider_user_id"), row.getString("display_name"), row.getString("email")))
                .optional();
    }
}
