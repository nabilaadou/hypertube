package com.hypertube.auth.service;

import java.text.Normalizer;
import java.util.Locale;
import com.hypertube.auth.exception.AuthException;
import com.hypertube.auth.repository.UserRepository;
import com.hypertube.auth.schema.LoginRequest;
import com.hypertube.auth.schema.RegistrationRequest;
import com.hypertube.auth.security.AuthTokens;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LocalAuthService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final TokenService tokens;
    private final LoginRateLimiter limiter;
    private final Argon2PasswordEncoder passwords = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final String dummyHash = passwords.encode("not-a-real-password");

    public LocalAuthService(JdbcTemplate jdbc, UserRepository users, TokenService tokens, LoginRateLimiter limiter) {
        this.jdbc = jdbc;
        this.users = users;
        this.tokens = tokens;
        this.limiter = limiter;
    }

    @Transactional
    public AuthTokens register(RegistrationRequest request) {
        String username = Normalizer.normalize(request.username(), Normalizer.Form.NFC);
        int length = username.codePointCount(0, username.length());
        if (length < 3 || length > 30 || !username.matches("[\\p{L}\\p{Nd}_]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must contain 3 to 30 letters, digits or underscores.");
        }
        String email = request.email().strip();
        var ids = jdbc.query("""
                INSERT INTO users (username, username_key, first_name, last_name, email, password_hash, display_name)
                VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING RETURNING id
                """, (row, i) -> row.getLong(1), username, usernameKey(username), request.firstName().strip(),
                request.lastName().strip(), email, passwords.encode(request.password()), username);
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email is already in use.");
        return tokens.createSession(users.findProfileById(ids.getFirst()).orElseThrow());
    }

    @Transactional
    public AuthTokens login(LoginRequest request, String address) {
        String key = usernameKey(request.username());
        var matches = jdbc.query("SELECT id, password_hash FROM users WHERE username_key = ?",
                (row, i) -> new Credential(row.getLong("id"), row.getString("password_hash")), key);
        Credential credential = matches.isEmpty() ? null : matches.getFirst();
        boolean valid = limiter.authenticate(key, address, () -> {
            String hash = credential == null || credential.hash() == null ? dummyHash : credential.hash();
            boolean matched = passwords.matches(request.password(), hash);
            return credential != null && credential.hash() != null && matched;
        });
        if (!valid) throw new AuthException("invalid_credentials", "Invalid username or password.", false);
        return tokens.createSession(users.findProfileById(credential.id()).orElseThrow());
    }

    static String usernameKey(String username) {
        String normalized = Normalizer.normalize(username, Normalizer.Form.NFC);
        return Normalizer.normalize(normalized.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT).replace("ß", "ss"), Normalizer.Form.NFC);
    }

    private record Credential(long id, String hash) {}
}
