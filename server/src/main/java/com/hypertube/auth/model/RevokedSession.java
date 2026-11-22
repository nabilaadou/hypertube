package com.hypertube.auth.model;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "revoked_sessions")
public class RevokedSession {
    @Id
    @Column(name = "session_id")
    private UUID sessionId;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    protected RevokedSession() {}
}
