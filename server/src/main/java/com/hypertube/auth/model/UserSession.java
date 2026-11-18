package com.hypertube.auth.model;

import java.time.Instant;
import java.util.UUID;

import com.hypertube.auth.security.DeviceMetadata;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_sessions")
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private long userId;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "refresh_token_hash", nullable = false, length = 64)
    private String refreshTokenHash;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "previous_token_hash", length = 64)
    private String previousTokenHash;
    @Column(name = "previous_token_expires_at")
    private Instant previousTokenExpiresAt;
    @Column(name = "device_info", nullable = false, length = 255)
    private String deviceInfo;
    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;
    @Column(name = "sliding_expires_at", nullable = false)
    private Instant slidingExpiresAt;
    @Column(name = "absolute_expires_at", nullable = false, updatable = false)
    private Instant absoluteExpiresAt;
    @Column(nullable = false)
    private boolean revoked;

    protected UserSession() {}

    public UserSession(long userId, String hash, DeviceMetadata device, Instant now, Instant sliding, Instant absolute) {
        this.userId = userId;
        this.refreshTokenHash = hash;
        this.deviceInfo = device.deviceInfo();
        this.ipAddress = device.ipAddress();
        this.createdAt = now;
        this.lastUsedAt = now;
        this.slidingExpiresAt = sliding;
        this.absoluteExpiresAt = absolute;
    }

    public UUID getId() { return id; }
    public long getUserId() { return userId; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public String getPreviousTokenHash() { return previousTokenHash; }
    public String getDeviceInfo() { return deviceInfo; }
    public String getIpAddress() { return ipAddress; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getSlidingExpiresAt() { return slidingExpiresAt; }
    public Instant getAbsoluteExpiresAt() { return absoluteExpiresAt; }
    public boolean isRevoked() { return revoked; }

    public boolean isActive(Instant now) {
        return !revoked && slidingExpiresAt.isAfter(now) && absoluteExpiresAt.isAfter(now);
    }

    public boolean isPreviousWithinGrace(String hash, Instant now) {
        return hash.equals(previousTokenHash) && previousTokenExpiresAt != null && previousTokenExpiresAt.isAfter(now);
    }

    public void rotate(String hash, DeviceMetadata device, Instant now, Instant sliding, Instant graceDeadline) {
        previousTokenHash = refreshTokenHash;
        previousTokenExpiresAt = graceDeadline;
        refreshTokenHash = hash;
        lastUsedAt = now;
        slidingExpiresAt = sliding;
        ipAddress = device.ipAddress();
        if (!device.deviceInfo().equals("Unknown device")) {
            deviceInfo = device.deviceInfo();
        }
    }

    public void revoke() { revoked = true; }
}
