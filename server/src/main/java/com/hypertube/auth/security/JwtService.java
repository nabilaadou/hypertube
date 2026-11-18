package com.hypertube.auth.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtProperties properties;
    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        var key = properties.signingKey();
        encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).validateType(false).build();
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        timestamps.setAllowEmptyExpiryClaim(false);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
                new JwtIssuerValidator(properties.issuer()), new JwtTypeValidator("at+jwt"), this::validateClaims));
    }

    public String issue(long userId, UUID sessionId, UUID tokenId, List<String> authorities, Instant issuedAt, Instant expiresAt) {
        var claims = JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
                .subject(Long.toString(userId)).id(tokenId.toString()).issuedAt(issuedAt).expiresAt(expiresAt)
                .claim("sid", sessionId.toString()).claim("authorities", authorities).claim("token_use", "access").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("at+jwt").build(), claims))
                .getTokenValue();
    }

    public Optional<AccessClaims> verify(String token) {
        if (token == null || token.isBlank() || token.length() > 4096) {
            return Optional.empty();
        }
        try {
            return Optional.of(claims(decoder.decode(token)));
        } catch (JwtException | IllegalArgumentException | ClassCastException e) {
            return Optional.empty();
        }
    }

    private OAuth2TokenValidatorResult validateClaims(Jwt jwt) {
        try {
            AccessClaims claims = claims(jwt);
            if (claims.userId() > 0 && !claims.authorities().isEmpty() && claims.authorities().size() <= 16
                    && claims.authorities().stream().allMatch(role -> role.matches("ROLE_[A-Z0-9_]{1,64}")) && jwt.getAudience().contains(properties.audience())
                    && "access".equals(jwt.getClaimAsString("token_use"))
                    && jwt.getIssuedAt() != null && !jwt.getIssuedAt().isAfter(clock.instant())
                    && claims.expiresAt() != null && claims.expiresAt().isAfter(jwt.getIssuedAt())
                    && claims.expiresAt().isAfter(clock.instant())) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (IllegalArgumentException | NullPointerException | ClassCastException e) {
            // Signed tokens still need valid claim types and identifiers.
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid access token claims", null));
    }

    private AccessClaims claims(Jwt jwt) {
        return new AccessClaims(Long.parseLong(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("sid")),
                UUID.fromString(jwt.getId()), List.copyOf(jwt.getClaimAsStringList("authorities")), jwt.getExpiresAt());
    }

    public record AccessClaims(long userId, UUID sessionId, UUID tokenId, List<String> authorities, Instant expiresAt) {}
}
