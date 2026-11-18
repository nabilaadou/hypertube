package com.hypertube.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

import com.hypertube.auth.model.RefreshGrant;
import com.hypertube.auth.model.RefreshToken;
import com.hypertube.auth.repository.RefreshGrantRepository;
import com.hypertube.auth.repository.RefreshTokenRepository;
import com.hypertube.auth.repository.RevokedAccessTokenRepository;
import com.hypertube.auth.repository.UserRepository;
import com.hypertube.auth.schema.UserProfile;
import com.hypertube.auth.security.AuthTokens;
import com.hypertube.auth.security.JwtService;
import com.hypertube.auth.security.TokenProperties;
import com.hypertube.auth.security.TokenValues;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {
    private final AuthService auth;
    private final RefreshGrantRepository grants;
    private final RefreshTokenRepository refreshTokens;
    private final RevokedAccessTokenRepository revokedTokens;
    private final UserRepository users;
    private final TokenProperties properties;
    private final JwtService jwt;
    private final Clock clock;

    public TokenService(AuthService auth, RefreshGrantRepository grants, RefreshTokenRepository refreshTokens,
                        RevokedAccessTokenRepository revokedTokens, UserRepository users,
                        TokenProperties properties, JwtService jwt, Clock clock) {
        this.auth = auth;
        this.grants = grants;
        this.refreshTokens = refreshTokens;
        this.revokedTokens = revokedTokens;
        this.users = users;
        this.properties = properties;
        this.jwt = jwt;
        this.clock = clock;
    }

    @Transactional
    public Optional<AuthTokens> completeLogin(UUID code, String proof) {
        return auth.finishLogin(code, proof).map(user -> {
            Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
            grants.deleteExpired(now);
            revokedTokens.deleteExpired(now);
            UUID tokenId = UUID.randomUUID();
            Instant accessExpiry = now.plus(properties.accessTtl()).truncatedTo(ChronoUnit.SECONDS);
            Instant refreshExpiry = now.plus(properties.refreshTtl()).truncatedTo(ChronoUnit.SECONDS);
            RefreshGrant grant = grants.save(new RefreshGrant(user.id(), tokenId, accessExpiry, refreshExpiry));
            String refreshToken = TokenValues.generate();
            refreshTokens.save(new RefreshToken(TokenValues.hash(refreshToken), grant.getId()));
            return new AuthTokens(user, jwt.issue(user.id(), grant.getId(), tokenId, now, accessExpiry), refreshToken,
                    Duration.between(now, accessExpiry), Duration.between(now, refreshExpiry));
        });
    }

    @Transactional(readOnly = true)
    public Optional<UserProfile> authenticate(String accessToken) {
        return jwt.verify(accessToken).filter(claims -> !revokedTokens.existsById(claims.tokenId()))
                .flatMap(claims -> users.findProfileById(claims.userId()));
    }

    @Transactional
    public Optional<AuthTokens> refresh(String refreshToken) {
        if (!TokenValues.isValid(refreshToken)) {
            return Optional.empty();
        }
        String hash = TokenValues.hash(refreshToken);
        // Lock the refresh grant before reading token state so concurrent refreshes cannot both succeed.
        var candidate = refreshTokens.findGrantId(hash).flatMap(grants::findLockedById);
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        if (candidate.isEmpty() || !candidate.get().isActive(now)
                || !candidate.get().getExpiresAt().truncatedTo(ChronoUnit.SECONDS).isAfter(now)) {
            return Optional.empty();
        }
        RefreshGrant grant = candidate.get();
        RefreshToken token = refreshTokens.findById(hash).orElseThrow();
        if (token.isUsed()) {
            revokeGrant(grant, now);
            return Optional.empty();
        }
        return users.findProfileById(grant.getUserId()).map(user -> {
            token.use(now);
            revokeAccessToken(grant.getAccessTokenId(), grant.getAccessExpiresAt(), now);
            UUID tokenId = UUID.randomUUID();
            Instant accessExpiry = now.plus(properties.accessTtl());
            if (accessExpiry.isAfter(grant.getExpiresAt())) {
                accessExpiry = grant.getExpiresAt();
            }
            accessExpiry = accessExpiry.truncatedTo(ChronoUnit.SECONDS);
            grant.rotateAccessToken(tokenId, accessExpiry);
            String newRefreshToken = TokenValues.generate();
            refreshTokens.save(new RefreshToken(TokenValues.hash(newRefreshToken), grant.getId()));
            return new AuthTokens(user, jwt.issue(user.id(), grant.getId(), tokenId, now, accessExpiry),
                    newRefreshToken, Duration.between(now, accessExpiry), Duration.between(now, grant.getExpiresAt()));
        });
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        var ids = new TreeSet<UUID>();
        var claims = jwt.verify(accessToken);
        claims.map(JwtService.AccessClaims::grantId).ifPresent(ids::add);
        if (TokenValues.isValid(refreshToken)) {
            refreshTokens.findGrantId(TokenValues.hash(refreshToken)).ifPresent(ids::add);
        }
        Instant now = clock.instant();
        ids.forEach(id -> grants.findLockedById(id).ifPresent(grant -> revokeGrant(grant, now)));
        claims.ifPresent(token -> revokeAccessToken(token.tokenId(), token.expiresAt(), now));
    }

    private void revokeGrant(RefreshGrant grant, Instant now) {
        grant.revoke(now);
        revokeAccessToken(grant.getAccessTokenId(), grant.getAccessExpiresAt(), now);
    }

    private void revokeAccessToken(UUID id, Instant expiresAt, Instant now) {
        if (expiresAt.isAfter(now)) {
            revokedTokens.revoke(id, expiresAt);
        }
    }
}
