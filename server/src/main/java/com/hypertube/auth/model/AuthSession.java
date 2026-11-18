package com.hypertube.auth.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "auth_sessions")
public class AuthSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "access_token_hash", nullable = false, length = 64)
    private String accessTokenHash;

    @Column(name = "access_expires_at", nullable = false)
    private Instant accessExpiresAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AuthSession() {}

    public AuthSession(long userId, String accessTokenHash, Instant accessExpiresAt, Instant expiresAt) {
        this.userId = userId;
        this.accessTokenHash = accessTokenHash;
        this.accessExpiresAt = accessExpiresAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public long getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean acceptsAccessToken(Instant now) {
        return isActive(now) && accessExpiresAt.isAfter(now);
    }

    public void rotateAccessToken(String hash, Instant expiresAt) {
        this.accessTokenHash = hash;
        this.accessExpiresAt = expiresAt;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
