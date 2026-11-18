package com.hypertube.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

import com.hypertube.auth.model.AuthSession;
import com.hypertube.auth.model.RefreshToken;
import com.hypertube.auth.repository.RefreshTokenRepository;
import com.hypertube.auth.repository.SessionRepository;
import com.hypertube.auth.repository.UserRepository;
import com.hypertube.auth.schema.UserProfile;
import com.hypertube.auth.security.SessionProperties;
import com.hypertube.auth.security.SessionTokens;
import com.hypertube.auth.security.TokenValues;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
    private final AuthService auth;
    private final SessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final SessionProperties properties;
    private final Clock clock;

    public SessionService(AuthService auth, SessionRepository sessions, RefreshTokenRepository refreshTokens,
                          UserRepository users, SessionProperties properties, Clock clock) {
        this.auth = auth;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Optional<SessionTokens> completeLogin(UUID code, String proof) {
        return auth.finishLogin(code, proof).map(user -> {
            Instant now = clock.instant();
            sessions.deleteExpired(now);
            String accessToken = TokenValues.generate();
            String refreshToken = TokenValues.generate();
            AuthSession session = sessions.save(new AuthSession(user.id(), TokenValues.hash(accessToken),
                    now.plus(properties.accessTtl()), now.plus(properties.refreshTtl())));
            refreshTokens.save(new RefreshToken(TokenValues.hash(refreshToken), session.getId()));
            return new SessionTokens(user, accessToken, refreshToken, properties.accessTtl(), properties.refreshTtl());
        });
    }

    @Transactional(readOnly = true)
    public Optional<UserProfile> authenticate(String accessToken) {
        if (!TokenValues.isValid(accessToken)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return sessions.findByAccessTokenHash(TokenValues.hash(accessToken))
                .filter(session -> session.acceptsAccessToken(now))
                .flatMap(session -> users.findProfileById(session.getUserId()));
    }

    @Transactional
    public Optional<SessionTokens> refresh(String refreshToken) {
        if (!TokenValues.isValid(refreshToken)) {
            return Optional.empty();
        }
        String hash = TokenValues.hash(refreshToken);
        // Lock the session before reading token state so concurrent refreshes cannot both succeed.
        var session = refreshTokens.findSessionId(hash).flatMap(sessions::findLockedById);
        Instant now = clock.instant();
        if (session.isEmpty() || !session.get().isActive(now)) {
            return Optional.empty();
        }
        RefreshToken token = refreshTokens.findById(hash).orElseThrow();
        if (token.isUsed()) {
            session.get().revoke(now);
            return Optional.empty();
        }
        return users.findProfileById(session.get().getUserId()).map(user -> {
            token.use(now);
            String newAccessToken = TokenValues.generate();
            String newRefreshToken = TokenValues.generate();
            Instant accessExpiry = now.plus(properties.accessTtl());
            if (accessExpiry.isAfter(session.get().getExpiresAt())) {
                accessExpiry = session.get().getExpiresAt();
            }
            session.get().rotateAccessToken(TokenValues.hash(newAccessToken), accessExpiry);
            refreshTokens.save(new RefreshToken(TokenValues.hash(newRefreshToken), session.get().getId()));
            return new SessionTokens(user, newAccessToken, newRefreshToken,
                    Duration.between(now, accessExpiry), Duration.between(now, session.get().getExpiresAt()));
        });
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        var ids = new TreeSet<UUID>();
        if (TokenValues.isValid(accessToken)) {
            sessions.findIdByAccessTokenHash(TokenValues.hash(accessToken)).ifPresent(ids::add);
        }
        if (TokenValues.isValid(refreshToken)) {
            refreshTokens.findSessionId(TokenValues.hash(refreshToken)).ifPresent(ids::add);
        }
        Instant now = clock.instant();
        ids.forEach(id -> sessions.findLockedById(id).ifPresent(session -> session.revoke(now)));
    }
}
