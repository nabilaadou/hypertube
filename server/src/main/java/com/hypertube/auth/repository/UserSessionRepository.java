package com.hypertube.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.hypertube.auth.model.UserSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends Repository<UserSession, UUID> {
    UserSession save(UserSession session);

    @Query("select s.id from UserSession s where s.refreshTokenHash = :hash or s.previousTokenHash = :hash")
    Optional<UUID> findIdByTokenHash(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserSession s where s.id = :id")
    Optional<UserSession> findLockedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserSession s where s.userId = :userId and s.revoked = false order by s.id")
    List<UserSession> findAllLockedByUserId(@Param("userId") long userId);

    @Query("select s from UserSession s where s.userId = :userId and s.revoked = false and s.slidingExpiresAt > :now and s.absoluteExpiresAt > :now order by s.lastUsedAt desc, s.id")
    List<UserSession> findActiveByUserId(@Param("userId") long userId, @Param("now") Instant now);

    @Modifying
    @Query(value = """
            DELETE FROM user_sessions WHERE id IN (
                SELECT id FROM user_sessions
                WHERE revoked OR sliding_expires_at <= :now OR absolute_expires_at <= :now
                ORDER BY absolute_expires_at LIMIT 1000 FOR UPDATE SKIP LOCKED
            )
            """, nativeQuery = true)
    int deleteExpiredAndRevoked(@Param("now") Instant now);
}
