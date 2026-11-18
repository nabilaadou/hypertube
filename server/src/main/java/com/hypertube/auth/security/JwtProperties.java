package com.hypertube.auth.security;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hypertube.auth.jwt")
public record JwtProperties(String secret, String issuer, String audience) {
    public JwtProperties {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience must be configured");
        }
    }

    public SecretKey signingKey() {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret == null ? "" : secret);
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
        return "JwtProperties[issuer=" + issuer + ", audience=" + audience + "]";
    }
}
