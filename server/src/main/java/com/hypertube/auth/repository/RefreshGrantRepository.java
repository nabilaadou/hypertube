package com.hypertube.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.hypertube.auth.model.RefreshGrant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface RefreshGrantRepository extends Repository<RefreshGrant, UUID> {
    RefreshGrant save(RefreshGrant session);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshGrant s where s.id = :id")
    Optional<RefreshGrant> findLockedById(@Param("id") UUID id);

    @Modifying
    @Query("delete from RefreshGrant s where s.expiresAt <= :now")
    void deleteExpired(@Param("now") Instant now);
}
