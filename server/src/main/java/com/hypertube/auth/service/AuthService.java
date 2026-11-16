package com.hypertube.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.hypertube.auth.model.LoginHandoff;
import com.hypertube.auth.model.OAuthIdentity;
import com.hypertube.auth.model.UserProfile;
import com.hypertube.auth.repository.AuthUserRepository;
import com.hypertube.auth.repository.LoginHandoffRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Pattern PROOF = Pattern.compile("[0-9a-f]{64}");
    private final LoginHandoffRepository handoffs;
    private final AuthUserRepository users;
    private final byte[] handoffAuthorization;

    AuthService(LoginHandoffRepository handoffs, AuthUserRepository users,
                @Value("${hypertube.auth.handoff-secret}") String secret) {
        this.handoffs = handoffs;
        this.users = users;
        this.handoffAuthorization = secret.length() >= 64 ? digest("Bearer " + secret) : null;
    }

    public boolean isHandoffAuthorized(String authorization) {
        return handoffAuthorization != null && MessageDigest.isEqual(handoffAuthorization, digest(authorization));
    }

    @Transactional
    public UUID startLogin(OAuthIdentity identity) {
        handoffs.deleteExpired();
        return handoffs.save(new LoginHandoff(identity)).getCode();
    }

    @Transactional
    public Optional<Long> finishLogin(UUID code, String proof) {
        if (!PROOF.matcher(proof).matches()) {
            return Optional.empty();
        }
        return handoffs.redeem(code, HexFormat.of().formatHex(digest(proof)));
    }

    @Transactional(readOnly = true)
    public Optional<UserProfile> findProfile(long id) {
        return users.findProfileById(id);
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
