package com.hypertube.auth.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    private static final byte[] KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID SESSION = UUID.fromString("49843834-5e9a-4c98-9c6d-e66a5cdd6290");
    private static final UUID TOKEN = UUID.fromString("858eb476-2f12-43f5-9625-c762d6402be9");
    private final JwtService jwt = new JwtService(new JwtProperties(
            Base64.getEncoder().encodeToString(KEY), "hypertube", "hypertube-api"), CLOCK);

    @Test
    void signsAndVerifiesAccessJwtClaims() {
        String token = jwt.issueAccessToken(17, SESSION, TOKEN, NOW, NOW.plusSeconds(900));
        var claims = jwt.verify(token).orElseThrow();
        assertThat(token.split("\\.")).hasSize(3);
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(payload).doesNotContain("\"authorities\"", "\"roles\"");
        assertThat(claims.userId()).isEqualTo(17);
        assertThat(claims.sessionId()).isEqualTo(SESSION);
        assertThat(claims.tokenId()).isEqualTo(TOKEN);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(900));
    }

    @Test
    void rejectsExpiredTokensIncludingExactExpiration() {
        for (Instant expiry : List.of(NOW.minusSeconds(1), NOW)) {
            assertThat(jwt.verify(jwt.issueAccessToken(17, SESSION, TOKEN, NOW.minusSeconds(60), expiry))).isEmpty();
        }
    }

    @Test
    void rejectsTamperingWrongSigningKeyAndUnsignedTokens() {
        String token = jwt.issueAccessToken(17, SESSION, TOKEN, NOW, NOW.plusSeconds(900));
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
        String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(
                payload.replace("\"sub\":\"17\"", "\"sub\":\"99\"").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(jwt.verify(parts[0] + "." + tampered + "." + parts[2])).isEmpty();
        byte[] otherKey = new byte[64];
        assertThat(jwt.verify(sign(validClaims().build(), MacAlgorithm.HS256, "at+jwt", otherKey))).isEmpty();
        String unsignedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"none\",\"typ\":\"at+jwt\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(jwt.verify(unsignedHeader + "." + parts[1] + ".")).isEmpty();
    }

    @Test
    void pinsTheSigningAlgorithmAndAccessTokenType() {
        assertThat(jwt.verify(sign(validClaims().build(), MacAlgorithm.HS512, "at+jwt", KEY))).isEmpty();
        assertThat(jwt.verify(sign(validClaims().build(), MacAlgorithm.HS256, "JWT", KEY))).isEmpty();
        assertThat(jwt.verify(sign(validClaims().build(), MacAlgorithm.HS256, "refresh+jwt", KEY))).isEmpty();
    }

    @Test
    void requiresCorrectIssuerAudiencePurposeAndIdentifiers() {
        var invalid = List.of(
                validClaims().issuer("other").build(),
                validClaims().audience(List.of("other-api")).build(),
                validClaims().claim("token_use", "refresh").build(),
                validClaims().subject("not-a-user-id").build(),
                validClaims().subject("-1").build(),
                validClaims().id("not-a-uuid").build(),
                validClaims().claim("sid", "not-a-uuid").build(),
                validClaims().claims(claims -> claims.remove("sid")).build(),
                validClaims().claims(claims -> claims.remove("exp")).build(),
                validClaims().claims(claims -> claims.remove("iat")).build(),
                validClaims().claims(claims -> claims.remove("aud")).build(),
                validClaims().issuedAt(NOW.plusSeconds(30)).build(),
                validClaims().notBefore(NOW.plusSeconds(30)).build());
        for (var claims : invalid) {
            assertThat(jwt.verify(sign(claims, MacAlgorithm.HS256, "at+jwt", KEY))).isEmpty();
        }
    }

    @Test
    void rejectsMalformedAndOversizedTokens() {
        for (String token : List.of("", "invalid", "a.b.c", "a".repeat(4097), jwt.issueRefreshToken())) {
            assertThat(jwt.verify(token)).isEmpty();
        }
        assertThat(jwt.verify(null)).isEmpty();
    }

    @Test
    void requiresAnExplicitStrongSigningKeyAndDoesNotExposeItInToString() {
        for (String key : List.of("", "invalid base64", Base64.getEncoder().encodeToString(new byte[16]))) {
            var properties = new JwtProperties(key, "hypertube", "hypertube-api");
            assertThatThrownBy(() -> new JwtService(properties, CLOCK)).isInstanceOf(IllegalArgumentException.class);
        }
        var properties = new JwtProperties(Base64.getEncoder().encodeToString(KEY), "hypertube", "hypertube-api");
        assertThat(properties.toString()).doesNotContain(properties.secret());
    }

    private JwtClaimsSet.Builder validClaims() {
        return JwtClaimsSet.builder().issuer("hypertube").audience(List.of("hypertube-api"))
                .subject("17").id(TOKEN.toString()).issuedAt(NOW).expiresAt(NOW.plusSeconds(900))
                .claim("sid", SESSION.toString()).claim("token_use", "access");
    }

    private String sign(JwtClaimsSet claims, MacAlgorithm algorithm, String type, byte[] key) {
        var encoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256")).algorithm(algorithm).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).type(type).build(), claims)).getTokenValue();
    }
}
