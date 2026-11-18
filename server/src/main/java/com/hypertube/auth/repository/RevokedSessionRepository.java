package com.hypertube.auth.repository;

import java.time.Instant;
import java.util.UUID;
import com.hypertube.auth.model.RevokedSession;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface RevokedSessionRepository extends Repository<RevokedSession, UUID> {
    boolean existsById(UUID id);

    @Modifying
    @Query(value = """
            INSERT INTO revoked_sessions (session_id, expires_at) VALUES (:id, :expiresAt)
            ON CONFLICT (session_id) DO UPDATE SET expires_at = GREATEST(revoked_sessions.expires_at, EXCLUDED.expires_at)
            """, nativeQuery = true)
    void revoke(@Param("id") UUID id, @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Query(value = """
            DELETE FROM revoked_sessions WHERE session_id IN (
                SELECT session_id FROM revoked_sessions WHERE expires_at <= :now
                ORDER BY expires_at LIMIT 1000 FOR UPDATE SKIP LOCKED
            )
            """, nativeQuery = true)
    int deleteExpired(@Param("now") Instant now);
}
