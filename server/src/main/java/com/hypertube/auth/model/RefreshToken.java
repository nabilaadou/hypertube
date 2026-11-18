package com.hypertube.auth.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "auth_refresh_tokens")
public class RefreshToken {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "used_at")
    private Instant usedAt;

    protected RefreshToken() {}

    public RefreshToken(String tokenHash, UUID sessionId) {
        this.tokenHash = tokenHash;
        this.sessionId = sessionId;
    }

    public boolean isUsed() { return usedAt != null; }

    public void use(Instant now) { usedAt = now; }
}
