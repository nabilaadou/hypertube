package com.hypertube.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import com.hypertube.auth.exception.AuthException;
import com.hypertube.auth.model.RefreshTokenHistory;
import com.hypertube.auth.model.UserSession;
import com.hypertube.auth.repository.RefreshTokenHistoryRepository;
import com.hypertube.auth.repository.RevokedSessionRepository;
import com.hypertube.auth.repository.UserRepository;
import com.hypertube.auth.repository.UserSessionRepository;
import com.hypertube.auth.schema.AuthResponse;
import com.hypertube.auth.schema.UserProfile;
import com.hypertube.auth.security.AuthTokens;
import com.hypertube.auth.security.JwtService;
import com.hypertube.auth.security.SessionPrincipal;
import com.hypertube.auth.security.TokenProperties;
import com.hypertube.auth.security.TokenValues;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {
    private final AuthService auth;
    private final UserSessionRepository sessions;
    private final RefreshTokenHistoryRepository history;
    private final RevokedSessionRepository revocations;
    private final UserRepository users;
    private final TokenProperties properties;
    private final JwtService jwt;
    private final Clock clock;

    public TokenService(AuthService auth, UserSessionRepository sessions, RefreshTokenHistoryRepository history,
                        RevokedSessionRepository revocations, UserRepository users,
                        TokenProperties properties, JwtService jwt, Clock clock) {
        this.auth = auth;
        this.sessions = sessions;
        this.history = history;
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
        Instant now = clock.instant();
        Instant absolute = now.plus(properties.absoluteTtl());
        Instant sliding = earlier(now.plus(properties.slidingTtl()), absolute);
        String refreshToken = TokenValues.generate();
        UserSession session = sessions.save(new UserSession(user.id(), TokenValues.hash(refreshToken), sliding, absolute));
        return issue(session, user, refreshToken, now);
    }

    @Transactional(readOnly = true)
    public Optional<SessionPrincipal> authenticate(String accessToken) {
        return jwt.verify(accessToken).filter(claims -> !revocations.existsById(claims.sessionId()))
                .map(claims -> new SessionPrincipal(claims.userId(), claims.sessionId()));
    }

    @Transactional(noRollbackFor = AuthException.class)
    public AuthTokens refresh(String refreshToken) {
        if (!TokenValues.isValid(refreshToken)) {
            throw invalidRefresh();
        }
        String hash = TokenValues.hash(refreshToken);
        // Resolve only an ID before taking the lock; reading token state earlier would cache stale data.
        UserSession session = findSessionId(hash).flatMap(sessions::findLockedById).orElseThrow(this::invalidRefresh);
        Instant now = clock.instant();
        if (!session.isActive(now)) {
            revoke(session);
            throw new AuthException("session_expired", "The session is expired or revoked", true);
        }
        boolean current = hash.equals(session.getRefreshTokenHash());
        boolean grace = session.isPreviousWithinGrace(hash, now)
                || (!current && history.findGraceDeadline(hash).filter(deadline -> deadline.isAfter(now)).isPresent());
        if (!current && !grace) {
            revoke(session);
            throw new AuthException("refresh_reuse", "Refresh token reuse detected; sign in again", true);
        }
        UserProfile user = users.findProfileById(session.getUserId()).orElseThrow(this::invalidRefresh);
        if (grace) {
            // Do not issue a refresh cookie: an out-of-order duplicate must not overwrite the winner's token.
            return issue(session, user, null, now);
        }
        String next = TokenValues.generate();
        history.save(new RefreshTokenHistory(hash, session.getId(), now.plus(properties.refreshGrace())));
        session.rotate(TokenValues.hash(next),
                earlier(now.plus(properties.slidingTtl()), session.getAbsoluteExpiresAt()), now.plus(properties.refreshGrace()));
        return issue(session, user, next, now);
    }

    @Transactional
    public void logout(SessionPrincipal principal, String refreshToken) {
        Optional<UUID> id = principal != null ? Optional.of(principal.sessionId())
                : TokenValues.isValid(refreshToken) ? findSessionId(TokenValues.hash(refreshToken)) : Optional.empty();
        id.flatMap(sessions::findLockedById).ifPresent(this::revoke);
    }

    @Transactional(readOnly = true)
    public UserProfile currentUser(SessionPrincipal principal) {
        return users.findProfileById(principal.userId())
                .orElseThrow(() -> new AuthException("user_not_found", "The authenticated user no longer exists", false));
    }

    private Optional<UUID> findSessionId(String hash) {
        return sessions.findIdByTokenHash(hash).or(() -> history.findSessionId(hash));
    }

    private AuthTokens issue(UserSession session, UserProfile user, String refreshToken, Instant now) {
        Instant issuedAt = now.truncatedTo(ChronoUnit.SECONDS);
        Instant expiry = earlier(issuedAt.plus(properties.accessTtl()),
                earlier(session.getSlidingExpiresAt(), session.getAbsoluteExpiresAt())).truncatedTo(ChronoUnit.SECONDS);
        if (!expiry.isAfter(now)) {
            revoke(session);
            throw new AuthException("session_expired", "The session is expired", true);
        }
        String accessToken = jwt.issue(user.id(), session.getId(), UUID.randomUUID(), issuedAt, expiry);
        return new AuthTokens(new AuthResponse(accessToken, "Bearer", Duration.between(issuedAt, expiry).toSeconds(), expiry, user),
                refreshToken, Duration.between(now, session.getSlidingExpiresAt()));
    }

    private void revoke(UserSession session) {
        session.revoke();
        // Keep the tombstone after session cleanup until no JWT from this session can remain valid.
        revocations.revoke(session.getId(), session.getAbsoluteExpiresAt());
    }

    private AuthException invalidRefresh() {
        return new AuthException("invalid_refresh", "The refresh token is missing or invalid", true);
    }

    private static Instant earlier(Instant first, Instant second) { return first.isBefore(second) ? first : second; }
}
