package com.hypertube.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.hypertube.auth.exception.AuthException;
import com.hypertube.auth.model.UserSession;
import com.hypertube.auth.repository.RevokedSessionRepository;
import com.hypertube.auth.repository.UserRepository;
import com.hypertube.auth.repository.UserSessionRepository;
import com.hypertube.auth.schema.UserProfile;
import com.hypertube.auth.security.AuthProperties;
import com.hypertube.auth.security.AuthTokens;
import com.hypertube.auth.security.JwtService;
import com.hypertube.auth.security.SessionPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {
    private final AuthService auth;
    private final UserSessionRepository sessions;
    private final RevokedSessionRepository revocations;
    private final UserRepository users;
    private final AuthProperties properties;
    private final JwtService jwt;
    private final Clock clock;

    public TokenService(AuthService auth, UserSessionRepository sessions,
                        RevokedSessionRepository revocations, UserRepository users,
                        AuthProperties properties, JwtService jwt, Clock clock) {
        this.auth = auth;
        this.sessions = sessions;
        this.revocations = revocations;
        this.users = users;
        this.properties = properties;
        this.jwt = jwt;
        this.clock = clock;
    }

    @Transactional
    public AuthTokens completeLogin(UUID code, String proof) {
        UserProfile user = auth.finishLogin(code, proof)
                .orElseThrow(() -> new AuthException("invalid_handoff", "The login handoff is invalid or expired", false));
        return createSession(user);
    }

    @Transactional
    public AuthTokens createSession(UserProfile user) {
        Instant now = clock.instant();
        String refreshToken = jwt.issueRefreshToken();
        UserSession session = sessions.save(new UserSession(user.id(), jwt.hash(refreshToken), now.plus(properties.refreshTtl())));
        return issue(session, user, refreshToken, now);
    }

    @Transactional(readOnly = true)
    public Optional<SessionPrincipal> authenticate(String accessToken) {
        return jwt.verify(accessToken).filter(claims -> !revocations.existsById(claims.sessionId()))
                .map(claims -> new SessionPrincipal(claims.userId(), claims.sessionId()));
    }

    @Transactional(noRollbackFor = AuthException.class)
    public AuthTokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw invalidRefresh();
        }
        UserSession session = sessions.findLockedByTokenHash(jwt.hash(refreshToken)).orElseThrow(this::invalidRefresh);
        Instant now = clock.instant();
        if (!session.isActive(now)) {
            revoke(session);
            throw new AuthException("session_expired", "The session is expired or revoked", true);
        }
        UserProfile user = users.findProfileById(session.getUserId()).orElseThrow(this::invalidRefresh);
        String next = jwt.issueRefreshToken();
        session.rotate(jwt.hash(next));
        return issue(session, user, next, now);
    }

    @Transactional
    public void logout(SessionPrincipal principal, String refreshToken) {
        Optional<UserSession> session = Optional.empty();
        if (principal != null) {
            session = sessions.findLockedById(principal.sessionId());
        } else if (refreshToken != null && !refreshToken.isEmpty()) {
            session = sessions.findLockedByTokenHash(jwt.hash(refreshToken));
        }
        session.ifPresent(this::revoke);
    }

    @Transactional(readOnly = true)
    public UserProfile currentUser(SessionPrincipal principal) {
        return users.findProfileById(principal.userId())
                .orElseThrow(() -> new AuthException("user_not_found", "The authenticated user no longer exists", false));
    }

    private AuthTokens issue(UserSession session, UserProfile user, String refreshToken, Instant now) {
        Instant expiry = now.plus(properties.accessTtl());
        if (expiry.isAfter(session.getExpiresAt())) {
            expiry = session.getExpiresAt();
        }
        if (!expiry.isAfter(now)) {
            revoke(session);
            throw new AuthException("session_expired", "The session is expired", true);
        }
        String accessToken = jwt.issueAccessToken(user.id(), session.getId(), UUID.randomUUID(), now, expiry);
        return new AuthTokens(accessToken, refreshToken, Duration.between(now, expiry),
                Duration.between(now, session.getExpiresAt()), expiry, user);
    }

    private void revoke(UserSession session) {
        session.revoke();
        // Keep the tombstone after session cleanup until no JWT from this session can remain valid.
        revocations.revoke(session.getId(), session.getExpiresAt());
    }

    private AuthException invalidRefresh() {
        return new AuthException("invalid_refresh", "The refresh token is missing or invalid", true);
    }
}
