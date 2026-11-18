package com.hypertube.auth.model;

import java.util.UUID;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "refresh_token_history")
public class RefreshTokenHistory {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", length = 64)
    private String tokenHash;
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "grace_expires_at", nullable = false)
    private Instant graceExpiresAt;

    protected RefreshTokenHistory() {}
    public RefreshTokenHistory(String hash, UUID sessionId, Instant graceExpiresAt) {
        this.tokenHash = hash;
        this.sessionId = sessionId;
        this.graceExpiresAt = graceExpiresAt;
    }
}
