package com.hypertube.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
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
    private static final Set<String> PROVIDERS = Set.of("42", "google");
    private final AuthRepository repository;
    private final byte[] handoffAuthorization;

    AuthService(AuthRepository repository, @Value("${hypertube.auth.handoff-secret}") String secret) {
        this.repository = repository;
        this.handoffAuthorization = secret.length() >= 64 ? digest("Bearer " + secret) : null;
    }

    public boolean isHandoffAuthorized(String authorization) {
        return handoffAuthorization != null && MessageDigest.isEqual(handoffAuthorization, digest(authorization));
    }

    public Optional<UUID> startLogin(OAuthIdentity identity) {
        if (identity.provider() == null || !PROVIDERS.contains(identity.provider())
                || identity.providerUserId() == null || identity.providerUserId().isBlank()
                || !fits(identity.providerUserId(), 128) || !fits(identity.displayName(), 128)
                || !fits(identity.email(), 254) || identity.proofHash() == null
                || !PROOF.matcher(identity.proofHash()).matches()) {
            return Optional.empty();
        }
        return Optional.of(repository.startLogin(identity));
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

    private static boolean fits(String value, int max) {
        return value == null || (value.indexOf('\0') < 0 && value.codePointCount(0, value.length()) <= max);
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
