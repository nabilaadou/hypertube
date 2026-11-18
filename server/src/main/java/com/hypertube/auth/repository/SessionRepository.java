package com.hypertube.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.hypertube.auth.model.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends Repository<AuthSession, UUID> {
    AuthSession save(AuthSession session);

    Optional<AuthSession> findByAccessTokenHash(String accessTokenHash);

    @Query("select s.id from AuthSession s where s.accessTokenHash = :hash")
    Optional<UUID> findIdByAccessTokenHash(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.id = :id")
    Optional<AuthSession> findLockedById(@Param("id") UUID id);

    @Modifying
    @Query("delete from AuthSession s where s.expiresAt <= :now")
    void deleteExpired(@Param("now") Instant now);
}
