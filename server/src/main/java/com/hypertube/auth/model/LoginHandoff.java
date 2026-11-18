package com.hypertube.auth.model;

import java.time.Instant;
import java.util.UUID;

import com.hypertube.auth.schema.OAuthIdentity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "oauth_login_handoffs")
public class LoginHandoff {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID code;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "browser_proof_hash", nullable = false, length = 64)
    private String proofHash;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 128)
    private String providerUserId;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(length = 254)
    private String email;

    @Generated
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected LoginHandoff() {}

    public LoginHandoff(OAuthIdentity identity) {
        this.proofHash = identity.proofHash();
        this.provider = identity.provider();
        this.providerUserId = identity.providerUserId();
        this.displayName = identity.displayName();
        this.email = identity.email();
    }

    public UUID getCode() {
        return code;
    }
}
