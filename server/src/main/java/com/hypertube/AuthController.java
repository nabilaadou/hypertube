package com.hypertube;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class AuthController {
    private static final String SESSION_USER = "hypertube.userId";
    private static final Pattern PROOF = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> PROVIDERS = Set.of("42", "google");
    private final AuthUsers users;
    private final byte[] handoffAuthorization;

    AuthController(AuthUsers users, @Value("${hypertube.auth.handoff-secret}") String secret) {
        this.users = users;
        this.handoffAuthorization = secret.length() >= 64 ? digest("Bearer " + secret) : null;
    }

    @PostMapping("/internal/auth/identities")
    ResponseEntity<String> receiveIdentity(@RequestHeader(value = "Authorization", defaultValue = "") String authorization,
                                           @RequestBody Identity identity) {
        if (handoffAuthorization == null || !MessageDigest.isEqual(handoffAuthorization, digest(authorization))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (identity.provider() == null || !PROVIDERS.contains(identity.provider())
                || identity.providerUserId() == null || identity.providerUserId().isBlank()
                || !fits(identity.providerUserId(), 128) || !fits(identity.displayName(), 128)
                || !fits(identity.email(), 254) || identity.proofHash() == null
                || !PROOF.matcher(identity.proofHash()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        UUID code = users.startLogin(identity.proofHash(), identity.provider(), identity.providerUserId(),
                identity.displayName(), identity.email());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(code.toString());
    }

    @GetMapping("/api/auth/complete")
    ResponseEntity<Void> complete(@RequestParam UUID code, HttpServletRequest request,
                                  @CookieValue(value = "hypertube_login_proof", defaultValue = "") String proof) {
        var response = ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer");
        if (!PROOF.matcher(proof).matches()) {
            return response.build();
        }
        var userId = users.finishLogin(code, HexFormat.of().formatHex(digest(proof)));
        if (userId.isEmpty()) {
            return response.build();
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        request.getSession().setAttribute(SESSION_USER, userId.get());
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create("/api/auth/me"))
                .cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer").build();
    }

    @GetMapping("/api/auth/me")
    ResponseEntity<AuthUsers.Profile> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SESSION_USER) instanceof Long id) {
            var profile = users.find(id);
            if (profile.isPresent()) {
                return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profile.get());
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore()).build();
    }

    private static boolean fits(String value, int max) {
        return value == null || (value.indexOf('\0') < 0 && value.codePointCount(0, value.length()) <= max);
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    record Identity(String provider, String providerUserId, String displayName, String email, String proofHash) {}
}
