package com.hypertube.auth.repository;

import java.time.Instant;
import java.util.UUID;

import com.hypertube.auth.model.RevokedAccessToken;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface RevokedAccessTokenRepository extends Repository<RevokedAccessToken, UUID> {
    boolean existsById(UUID id);

    @Modifying
    @Query(value = """
            INSERT INTO auth_revoked_access_tokens (token_id, expires_at) VALUES (:id, :expiresAt)
            ON CONFLICT (token_id) DO NOTHING
            """, nativeQuery = true)
    void revoke(@Param("id") UUID id, @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Query("delete from RevokedAccessToken t where t.expiresAt <= :now")
    void deleteExpired(@Param("now") Instant now);
}
