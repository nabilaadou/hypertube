package com.hypertube.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import com.hypertube.auth.model.LoginHandoff;
import com.hypertube.auth.repository.LoginHandoffRepository;
import com.hypertube.auth.schema.OAuthIdentity;
import com.hypertube.auth.security.AuthProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final LoginHandoffRepository handoffs;
    private final java.time.Clock clock;
    private final byte[] handoffAuthorization;

    AuthService(LoginHandoffRepository handoffs, java.time.Clock clock, AuthProperties properties) {
        this.handoffs = handoffs;
        this.clock = clock;
        String secret = properties.handoffSecret();
        this.handoffAuthorization = secret != null && secret.length() >= 64 ? digest("Bearer " + secret) : null;
    }

    public boolean isHandoffAuthorized(String authorization) {
        return handoffAuthorization != null && MessageDigest.isEqual(handoffAuthorization, digest(authorization));
    }

    @Transactional
    public UUID startLogin(OAuthIdentity identity) {
        var now = clock.instant();
        handoffs.deleteExpired(now);
        return handoffs.save(new LoginHandoff(identity, now.plusSeconds(120))).getCode();
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
