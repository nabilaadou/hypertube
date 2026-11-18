package com.hypertube.auth.repository;

import java.util.Optional;
import java.util.UUID;
import com.hypertube.auth.model.RefreshTokenHistory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenHistoryRepository extends Repository<RefreshTokenHistory, String> {
    RefreshTokenHistory save(RefreshTokenHistory token);
    @Query("select t.sessionId from RefreshTokenHistory t where t.tokenHash = :hash")
    Optional<UUID> findSessionId(@Param("hash") String hash);
}
