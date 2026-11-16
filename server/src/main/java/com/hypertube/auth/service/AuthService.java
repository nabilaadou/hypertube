package com.hypertube.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.hypertube.auth.model.OAuthIdentity;
import com.hypertube.auth.model.UserProfile;
import com.hypertube.auth.repository.AuthRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final Pattern PROOF = Pattern.compile("[0-9a-f]{64}");
    private final AuthRepository repository;
    private final byte[] handoffAuthorization;

    AuthService(AuthRepository repository, @Value("${hypertube.auth.handoff-secret}") String secret) {
        this.repository = repository;
        this.handoffAuthorization = secret.length() >= 64 ? digest("Bearer " + secret) : null;
    }

    public boolean isHandoffAuthorized(String authorization) {
        return handoffAuthorization != null && MessageDigest.isEqual(handoffAuthorization, digest(authorization));
    }

    public UUID startLogin(OAuthIdentity identity) {
        return repository.startLogin(identity);
    }

    public Optional<Long> finishLogin(UUID code, String proof) {
        if (!PROOF.matcher(proof).matches()) {
            return Optional.empty();
        }
        return repository.finishLogin(code, HexFormat.of().formatHex(digest(proof)));
    }

    public Optional<UserProfile> findProfile(long id) {
        return repository.findProfile(id);
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
