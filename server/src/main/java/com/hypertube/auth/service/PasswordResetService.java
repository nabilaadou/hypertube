package com.hypertube.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import com.hypertube.auth.exception.AuthException;
import com.hypertube.auth.repository.UserRepository;
import com.hypertube.auth.schema.PasswordResetConfirmation;
import com.hypertube.auth.security.AuthTokens;
import com.hypertube.auth.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@Service
public class PasswordResetService {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetService.class);
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final TokenService tokens;
    private final JwtService random;
    private final PasswordResetEmailService mail;
    private final Clock clock;
    private final Argon2PasswordEncoder passwords = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final Map<String, Instant> requests = new HashMap<>();

    public PasswordResetService(JdbcTemplate jdbc, UserRepository users, TokenService tokens, JwtService random,
            PasswordResetEmailService mail, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.tokens = tokens;
        this.random = random;
        this.mail = mail;
        this.clock = clock;
    }

    @Transactional
    public void request(String email) {
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        if (!allow(normalized, now)) return;
        var userIds = jdbc.query("SELECT id FROM users WHERE lower(email) = ? AND password_hash IS NOT NULL FOR UPDATE",
                (row, i) -> row.getLong(1), normalized);
        if (userIds.isEmpty()) return;
        String token = random.issueRefreshToken();
        jdbc.update("""
                INSERT INTO password_reset_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET token_hash = EXCLUDED.token_hash, expires_at = EXCLUDED.expires_at
                """, userIds.getFirst(), random.hash(token), java.sql.Timestamp.from(now.plus(Duration.ofMinutes(30))));
        try {
            mail.send(email.strip(), token);
        } catch (MailException failure) {
            LOG.warn("Could not send a password reset message");
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    @Transactional
    public AuthTokens confirm(PasswordResetConfirmation request) {
        String hash = random.hash(request.token());
        Instant now = clock.instant();
        var ids = jdbc.query("""
                SELECT u.id FROM users u JOIN password_reset_tokens t ON t.user_id = u.id
                WHERE t.token_hash = ? AND t.expires_at > ? AND u.password_hash IS NOT NULL FOR UPDATE OF u
                """, (row, i) -> row.getLong(1), hash, java.sql.Timestamp.from(now));
        if (ids.isEmpty()) throw invalidToken();
        long userId = ids.getFirst();
        if (jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = ? AND token_hash = ? AND expires_at > ?",
                userId, hash, java.sql.Timestamp.from(now)) != 1) throw invalidToken();
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", passwords.encode(request.password()), userId);
        jdbc.update("""
                INSERT INTO revoked_sessions (session_id, expires_at)
                SELECT id, expires_at FROM user_sessions WHERE user_id = ?
                ON CONFLICT (session_id) DO UPDATE SET expires_at = GREATEST(revoked_sessions.expires_at, EXCLUDED.expires_at)
                """, userId);
        jdbc.update("UPDATE user_sessions SET revoked = true WHERE user_id = ?", userId);
        return tokens.createSession(users.findProfileById(userId).orElseThrow());
    }

    private synchronized boolean allow(String email, Instant now) {
        requests.values().removeIf(expiry -> !now.isBefore(expiry));
        if (requests.containsKey(email)) return false;
        requests.put(email, now.plus(Duration.ofMinutes(1)));
        return true;
    }

    private AuthException invalidToken() {
        return new AuthException("invalid_reset", "The password reset link is invalid or expired.", false);
    }
}
