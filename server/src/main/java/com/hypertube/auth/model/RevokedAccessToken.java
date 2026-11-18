package com.hypertube.auth.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_revoked_access_tokens")
public class RevokedAccessToken {
    @Id
    @Column(name = "token_id")
    private UUID tokenId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected RevokedAccessToken() {}
}
