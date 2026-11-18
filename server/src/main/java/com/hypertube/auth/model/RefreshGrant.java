package com.hypertube.auth.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_refresh_grants")
public class RefreshGrant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "access_token_id", nullable = false)
    private UUID accessTokenId;

    @Column(name = "access_expires_at", nullable = false)
    private Instant accessExpiresAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshGrant() {}

    public RefreshGrant(long userId, UUID accessTokenId, Instant accessExpiresAt, Instant expiresAt) {
        this.userId = userId;
        this.accessTokenId = accessTokenId;
        this.accessExpiresAt = accessExpiresAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public long getUserId() { return userId; }
    public UUID getAccessTokenId() { return accessTokenId; }
    public Instant getAccessExpiresAt() { return accessExpiresAt; }
    public Instant getExpiresAt() { return expiresAt; }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void rotateAccessToken(UUID tokenId, Instant expiresAt) {
        this.accessTokenId = tokenId;
        this.accessExpiresAt = expiresAt;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
