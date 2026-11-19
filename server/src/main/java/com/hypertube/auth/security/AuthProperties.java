package com.hypertube.auth.security;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hypertube.auth")
public record AuthProperties(String handoffSecret, Duration accessTtl, Duration refreshTtl,
                             boolean secureCookies, Duration cleanupInterval,
                             String jwtSecret, String jwtIssuer, String jwtAudience) {
    public AuthProperties {
        if (accessTtl == null || refreshTtl == null || accessTtl.compareTo(Duration.ofSeconds(1)) < 0
                || refreshTtl.compareTo(accessTtl) < 0) {
            throw new IllegalArgumentException("Require 1s <= access-ttl <= refresh-ttl");
        }
        if (jwtIssuer == null || jwtIssuer.isBlank() || jwtAudience == null || jwtAudience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience must be configured");
        }
    }

    SecretKey signingKey() {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(jwtSecret == null ? "" : jwtSecret);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("AUTH_JWT_SECRET must be Base64-encoded random bytes");
        }
        if (bytes.length < 32) {
            throw new IllegalArgumentException("AUTH_JWT_SECRET must contain at least 32 random bytes; generate it with openssl rand -base64 32");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Override
    public String toString() {
        return "AuthProperties[accessTtl=" + accessTtl + ", refreshTtl=" + refreshTtl
                + ", secureCookies=" + secureCookies + ", cleanupInterval=" + cleanupInterval
                + ", jwtIssuer=" + jwtIssuer + ", jwtAudience=" + jwtAudience + "]";
    }
}
