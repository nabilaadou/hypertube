package com.hypertube.auth.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.hypertube.auth.exception.AuthException;
import com.hypertube.auth.repository.UserRepository;
import com.hypertube.auth.schema.LoginRequest;
import com.hypertube.auth.schema.ProfileUpdate;
import com.hypertube.auth.schema.UserProfile;
import com.hypertube.auth.security.JwtService;
import com.hypertube.auth.security.SessionPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmailChangeService {
    public record Result(UserProfile user, String emailChange) {}
    public record Challenge(UUID id, String secret) {}
    private record Account(String username, String email, String passwordHash, String provider, String providerId) {}
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final LocalAuthService local;
    private final JwtService random;
    private final Clock clock;
    private final EmailChangeEmailService mail;
    private final Map<String, Instant> limits = new HashMap<>();

    public EmailChangeService(JdbcTemplate jdbc, UserRepository users, LocalAuthService local, JwtService random,
            Clock clock, EmailChangeEmailService mail) {
        this.jdbc = jdbc; this.users = users; this.local = local; this.random = random;
        this.clock = clock; this.mail = mail;
    }

    @Transactional
    public Challenge start(SessionPrincipal principal) {
        Account account = lockAccount(principal.userId());
        requireActive(principal);
        if (account.passwordHash() != null || account.provider() == null) throw badRequest("Provider reauthentication is unavailable.");
        UUID id = UUID.randomUUID();
        String secret = random.issueRefreshToken();
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO profile_reauth_attempts (id, session_id, user_id, browser_hash, provider, provider_user_id, started_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (session_id) DO UPDATE SET
                id = EXCLUDED.id, user_id = EXCLUDED.user_id, browser_hash = EXCLUDED.browser_hash,
                provider = EXCLUDED.provider, provider_user_id = EXCLUDED.provider_user_id,
                started_at = EXCLUDED.started_at, expires_at = EXCLUDED.expires_at, verified = false
                """, id, principal.sessionId(), principal.userId(), random.hash(secret), account.provider(), account.providerId(),
                Timestamp.from(now), Timestamp.from(now.plusSeconds(120)));
        return new Challenge(id, secret);
    }

    @Transactional
    public void providerCallback(UUID id, String secret, String provider, String providerId) {
        if (secret == null || secret.isEmpty()) throw invalidReauth();
        int updated = jdbc.update("""
                UPDATE profile_reauth_attempts a SET verified = true
                FROM users u, user_sessions s WHERE a.id = ? AND a.browser_hash = ? AND a.expires_at > ?
                  AND NOT a.verified AND a.provider = ? AND a.provider_user_id = ?
                  AND u.id = a.user_id AND u.provider = a.provider AND u.provider_user_id = a.provider_user_id
                  AND s.id = a.session_id AND s.user_id = a.user_id AND NOT s.revoked AND s.expires_at > ?
                """, id, random.hash(secret), Timestamp.from(clock.instant()), provider, providerId, Timestamp.from(clock.instant()));
        if (updated != 1) throw invalidReauth();
    }

    @Transactional
    public Result update(SessionPrincipal principal, long id, ProfileUpdate request, String address, String secret) {
        if (principal.userId() != id) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        String username = request.username() == null ? null : LocalAuthService.validUsername(request.username());
        String email = request.email() == null ? null : request.email().strip();
        Account account = lockAccount(id);
        requireActive(principal);
        boolean changingEmail = email != null && !email.equalsIgnoreCase(account.email());
        if (changingEmail) {
            if (Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM users WHERE lower(email) = lower(?) AND id <> ?)", Boolean.class, email, id))) {
                throw conflict();
            }
            if (account.passwordHash() != null) {
                if (request.password() == null) throw invalidReauth();
                local.verify(new LoginRequest(account.username(), request.password()), address, id);
            } else {
                if (secret == null || secret.isEmpty()) throw invalidReauth();
                int consumed = jdbc.update("""
                        DELETE FROM profile_reauth_attempts WHERE session_id = ? AND user_id = ?
                        AND browser_hash = ? AND verified AND expires_at > ?
                        """, principal.sessionId(), id, random.hash(secret), Timestamp.from(clock.instant()));
                if (consumed != 1) throw invalidReauth();
            }
        }
        try {
            jdbc.update("""
                    UPDATE users SET username = COALESCE(?, username), username_key = COALESCE(?, username_key),
                    first_name = COALESCE(?, first_name), last_name = COALESCE(?, last_name) WHERE id = ?
                    """, username, username == null ? null : LocalAuthService.usernameKey(username),
                    request.firstName() == null ? null : request.firstName().strip(),
                    request.lastName() == null ? null : request.lastName().strip(), id);
        } catch (DataIntegrityViolationException duplicate) { throw conflict(); }
        String status = "unchanged";
        if (changingEmail) {
            if (!allow(id, email)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another email change.");
            String token = random.issueRefreshToken();
            try {
                mail.send(email, token);
                jdbc.update("""
                        INSERT INTO email_change_tokens (user_id, email, token_hash, expires_at) VALUES (?, ?, ?, ?)
                        ON CONFLICT (user_id) DO UPDATE SET email = EXCLUDED.email, token_hash = EXCLUDED.token_hash, expires_at = EXCLUDED.expires_at
                        """, id, email, random.hash(token), Timestamp.from(clock.instant().plus(Duration.ofMinutes(30))));
                status = "pending";
            } catch (MailException failure) { status = "delivery_failed"; }
        }
        return new Result(users.findProfileById(id).orElseThrow(), status);
    }

    @Transactional
    public void confirm(String token) {
        String hash = random.hash(token);
        var ids = jdbc.query("""
                SELECT u.id FROM users u JOIN email_change_tokens t ON t.user_id = u.id
                WHERE t.token_hash = ? AND t.expires_at > ? FOR UPDATE OF u
                """, (row, i) -> row.getLong(1), hash, Timestamp.from(clock.instant()));
        if (ids.isEmpty()) throw invalidToken();
        long id = ids.getFirst();
        var emails = jdbc.query("DELETE FROM email_change_tokens WHERE user_id = ? AND token_hash = ? AND expires_at > ? RETURNING email",
                (row, i) -> row.getString(1), id, hash, Timestamp.from(clock.instant()));
        if (emails.isEmpty()) throw invalidToken();
        try { jdbc.update("UPDATE users SET email = ? WHERE id = ?", emails.getFirst(), id); }
        catch (DataIntegrityViolationException duplicate) { throw conflict(); }
        jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = ?", id);
        jdbc.query("SELECT id FROM user_sessions WHERE user_id = ? ORDER BY id FOR UPDATE", (row, index) -> row.getObject(1), id);
        jdbc.update("""
                INSERT INTO revoked_sessions (session_id, expires_at)
                SELECT id, expires_at FROM user_sessions WHERE user_id = ?
                ON CONFLICT (session_id) DO UPDATE SET expires_at = GREATEST(revoked_sessions.expires_at, EXCLUDED.expires_at)
                """, id);
        jdbc.update("UPDATE user_sessions SET revoked = true WHERE user_id = ?", id);
    }

    private Account lockAccount(long id) {
        var rows = jdbc.query("SELECT username, email, password_hash, provider, provider_user_id FROM users WHERE id = ? FOR UPDATE",
                (r, i) -> new Account(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5)), id);
        if (rows.isEmpty()) throw invalidReauth();
        return rows.getFirst();
    }
    private void requireActive(SessionPrincipal p) {
        var rows = jdbc.query("SELECT id FROM user_sessions WHERE id = ? AND user_id = ? AND NOT revoked AND expires_at > ? FOR UPDATE",
                (r, i) -> r.getObject(1), p.sessionId(), p.userId(), Timestamp.from(clock.instant()));
        if (rows.isEmpty()) throw invalidReauth();
    }
    private synchronized boolean allow(long id, String email) {
        Instant now = clock.instant(); limits.values().removeIf(expiry -> !now.isBefore(expiry));
        String user = "u:" + id, destination = "e:" + email.toLowerCase(Locale.ROOT);
        if (limits.containsKey(user) || limits.containsKey(destination)) return false;
        limits.put(user, now.plusSeconds(60)); limits.put(destination, now.plusSeconds(60)); return true;
    }
    private AuthException invalidReauth() { return new AuthException("invalid_reauthentication", "Reauthentication is invalid or expired.", false); }
    private AuthException invalidToken() { return new AuthException("invalid_email_change", "The confirmation link is invalid or expired.", false); }
    private ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "Username or email is already in use."); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
