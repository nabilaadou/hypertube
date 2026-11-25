package com.hypertube.auth.repository;

import java.time.Instant;
import java.util.UUID;
import com.hypertube.auth.model.LoginHandoff;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface LoginHandoffRepository extends Repository<LoginHandoff, UUID> {
    LoginHandoff save(LoginHandoff handoff);

    @Modifying
    @Query("delete from LoginHandoff handoff where handoff.expiresAt <= :now")
    void deleteExpired(@Param("now") Instant now);
}
