package com.hypertube.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import com.hypertube.auth.exception.AuthException;
import com.hypertube.auth.repository.UserRepository;
import com.hypertube.auth.schema.LoginRequest;
import com.hypertube.auth.schema.OAuthProfileRequest;
import com.hypertube.auth.security.AuthTokens;
import com.hypertube.auth.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OAuthFlowService {
    public enum Status { PROFILE_REQUIRED, LINK_REQUIRED, AUTHENTICATED }
    public record Outcome(Status status, Instant expiresAt, AuthTokens tokens) {}
    private record Handoff(UUID code, String provider, String providerId, String email, Long targetUserId) {}
    private record Account(long id, String provider, String passwordHash, boolean complete) {}
    private final JdbcTemplate jdbc;
    private final JwtService jwt;
    private final Clock clock;
    private final LocalAuthService local;
    private final TokenService tokens;
    private final UserRepository users;

    public OAuthFlowService(JdbcTemplate jdbc, JwtService jwt, Clock clock, LocalAuthService local,
                            TokenService tokens, UserRepository users) {
        this.jdbc = jdbc;
        this.jwt = jwt;
        this.clock = clock;
        this.local = local;
        this.tokens = tokens;
        this.users = users;
    }

    @Transactional
    public Outcome begin(UUID code, String proof) {
        Handoff handoff = lock(code, proof, "INITIAL");
        Account existing = byProvider(handoff.provider(), handoff.providerId());
        if (existing != null) {
            if (existing.complete()) return signedIn(handoff, existing.id());
            return pending(handoff, Status.PROFILE_REQUIRED, existing.id(), 900, handoff.email());
        }
        Account matched = byEmail(handoff.email());
        if (matched != null) return linkOrReject(handoff, matched, handoff.email());
        return pending(handoff, Status.PROFILE_REQUIRED, null, 900, handoff.email());
    }

    @Transactional
    public Outcome profile(UUID code, String proof, OAuthProfileRequest request) {
        Handoff handoff = lock(code, proof, "PROFILE_REQUIRED");
        String username = LocalAuthService.validUsername(request.username());
        String email = request.email().strip();
        Account owner = byEmail(email);
        if (owner != null && !Long.valueOf(owner.id()).equals(handoff.targetUserId())) {
            if (handoff.targetUserId() != null) throw conflict();
            return linkOrReject(handoff, owner, email);
        }
        try {
            long id;
            if (handoff.targetUserId() == null) {
                var ids = jdbc.query("""
                        INSERT INTO users (provider, provider_user_id, username, username_key, first_name, last_name, email, display_name)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING RETURNING id
                        """, (row, i) -> row.getLong(1), handoff.provider(), handoff.providerId(), username,
                        LocalAuthService.usernameKey(username), request.firstName().strip(), request.lastName().strip(), email, username);
                if (ids.isEmpty()) throw conflict();
                id = ids.getFirst();
            } else {
                id = handoff.targetUserId();
                int updated = jdbc.update("""
                        UPDATE users SET username = ?, username_key = ?, first_name = ?, last_name = ?, email = ?,
                            display_name = COALESCE(display_name, ?) WHERE id = ? AND provider = ? AND provider_user_id = ?
                        """, username, LocalAuthService.usernameKey(username), request.firstName().strip(),
                        request.lastName().strip(), email, username, id, handoff.provider(), handoff.providerId());
                if (updated != 1) throw conflict();
            }
            return signedIn(handoff, id);
        } catch (DataIntegrityViolationException duplicate) {
            throw conflict();
        }
    }

    @Transactional
    public Outcome link(UUID code, String proof, LoginRequest request, String address) {
        Handoff handoff = lock(code, proof, "LINK_REQUIRED");
        var user = local.verify(request, address, handoff.targetUserId());
        try {
            int updated = jdbc.update("""
                    UPDATE users SET provider = ?, provider_user_id = ?
                    WHERE id = ? AND provider IS NULL AND password_hash IS NOT NULL AND lower(email) = lower(?)
                    """, handoff.provider(), handoff.providerId(), user.id(), handoff.email());
            if (updated != 1) throw conflict();
            return signedIn(handoff, user.id());
        } catch (DataIntegrityViolationException duplicate) {
            throw conflict();
        }
    }

    private Outcome linkOrReject(Handoff handoff, Account account, String email) {
        if (account.passwordHash() == null || account.provider() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Sign in with your original provider or password.");
        }
        return pending(handoff, Status.LINK_REQUIRED, account.id(), 120, email);
    }

    private Outcome pending(Handoff handoff, Status state, Long target, int seconds, String email) {
        Instant expiry = clock.instant().plusSeconds(seconds);
        jdbc.update("UPDATE oauth_login_handoffs SET state = ?, target_user_id = ?, expires_at = ?, email = ? WHERE code = ?",
                state.name(), target, java.sql.Timestamp.from(expiry), email, handoff.code());
        return new Outcome(state, expiry, null);
    }

    private Outcome signedIn(Handoff handoff, long userId) {
        jdbc.update("DELETE FROM oauth_login_handoffs WHERE code = ?", handoff.code());
        return new Outcome(Status.AUTHENTICATED, null, tokens.createSession(users.findProfileById(userId).orElseThrow()));
    }

    private Handoff lock(UUID code, String proof, String expectedState) {
        if (proof == null || !proof.matches("[0-9a-f]{64}")) throw invalid();
        var rows = jdbc.query("""
                SELECT code, provider, provider_user_id, email, target_user_id
                FROM oauth_login_handoffs WHERE code = ? AND browser_proof_hash = ? AND expires_at > ? AND state = ? FOR UPDATE
                """, (row, i) -> new Handoff(row.getObject("code", UUID.class), row.getString("provider"),
                    row.getString("provider_user_id"), row.getString("email"), (Long) row.getObject("target_user_id")),
                code, jwt.hash(proof), java.sql.Timestamp.from(clock.instant()), expectedState);
        if (rows.isEmpty()) throw invalid();
        return rows.getFirst();
    }

    private Account byProvider(String provider, String id) {
        var rows = jdbc.query("SELECT " + accountFields() + " FROM users WHERE provider = ? AND provider_user_id = ? FOR UPDATE",
                this::account, provider, id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Account byEmail(String email) {
        if (email == null || email.isBlank()) return null;
        var rows = jdbc.query("SELECT " + accountFields() + " FROM users WHERE lower(email) = lower(?) FOR UPDATE", this::account, email.strip());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private String accountFields() {
        return "id, provider, password_hash, "
                + "(username IS NOT NULL AND first_name IS NOT NULL AND last_name IS NOT NULL AND email IS NOT NULL) AS complete";
    }

    private Account account(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        return new Account(row.getLong("id"), row.getString("provider"), row.getString("password_hash"), row.getBoolean("complete"));
    }

    private AuthException invalid() { return new AuthException("invalid_handoff", "The login handoff is invalid or expired", false); }
    private ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "The account cannot be linked or the profile is already in use."); }
}
