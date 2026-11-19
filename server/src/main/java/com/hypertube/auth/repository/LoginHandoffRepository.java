package com.hypertube.auth.repository;

import java.util.Optional;
import java.util.UUID;

import com.hypertube.auth.model.LoginHandoff;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface LoginHandoffRepository extends Repository<LoginHandoff, UUID> {
    LoginHandoff save(LoginHandoff handoff);

    @Modifying
    @Query("delete from LoginHandoff handoff where handoff.expiresAt <= current_timestamp")
    void deleteExpired();

    // Consume the code and upsert the user in one statement so concurrent logins can safely share an account.
    @NativeQuery("""
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
            """)
    Optional<Long> redeem(@Param("code") UUID code, @Param("proofHash") String proofHash);
}
