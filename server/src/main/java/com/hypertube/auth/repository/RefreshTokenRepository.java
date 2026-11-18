package com.hypertube.auth.repository;

import java.util.Optional;
import java.util.UUID;

import com.hypertube.auth.model.RefreshToken;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends Repository<RefreshToken, String> {
    RefreshToken save(RefreshToken token);
    Optional<RefreshToken> findById(String hash);

    @Query("select t.sessionId from RefreshToken t where t.tokenHash = :hash")
    Optional<UUID> findSessionId(@Param("hash") String hash);
}
