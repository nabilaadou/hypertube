package com.hypertube.auth.controller;

import java.util.UUID;

import com.hypertube.auth.schema.OAuthIdentity;
import com.hypertube.auth.schema.UserProfile;
import com.hypertube.auth.security.SessionCookies;
import com.hypertube.auth.security.SessionTokens;
import com.hypertube.auth.service.AuthService;
import com.hypertube.auth.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
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
    private final AuthService auth;
    private final SessionService sessions;
    private final SessionCookies cookies;

    AuthController(AuthService auth, SessionService sessions, SessionCookies cookies) {
        this.auth = auth;
        this.sessions = sessions;
        this.cookies = cookies;
    }

    @PostMapping("/internal/auth/login-handoffs")
    ResponseEntity<String> createHandoff(
            @RequestHeader(value = "Authorization", defaultValue = "") String authorization,
            @Valid @RequestBody OAuthIdentity identity)
    {
        if (!auth.isHandoffAuthorized(authorization)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        UUID code = auth.startLogin(identity);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(code.toString());
    }

    @PostMapping("/api/auth/login-handoffs/complete")
    ResponseEntity<UserProfile> completeHandoff(
            @RequestParam UUID code,
            @CookieValue(value = "hypertube_login_proof", defaultValue = "") String proof)
    {
        return sessions.completeLogin(code, proof).map(this::signedIn)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore())
                        .header("Referrer-Policy", "no-referrer").build());
    }

    @PostMapping("/api/auth/refresh")
    ResponseEntity<UserProfile> refresh(
            @CookieValue(value = SessionCookies.REFRESH, defaultValue = "") String refreshToken) {
        return sessions.refresh(refreshToken).map(this::signedIn)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore())
                        .headers(cookies.clear()).build());
    }

    @PostMapping("/api/auth/logout")
    ResponseEntity<Void> logout(
            @CookieValue(value = SessionCookies.ACCESS, defaultValue = "") String accessToken,
            @CookieValue(value = SessionCookies.REFRESH, defaultValue = "") String refreshToken) {
        sessions.logout(accessToken, refreshToken);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).headers(cookies.clear()).build();
    }

    @GetMapping("/api/auth/me")
    ResponseEntity<UserProfile> currentUser(@AuthenticationPrincipal UserProfile user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(user);
    }

    @GetMapping("/api/auth/csrf")
    ResponseEntity<CsrfToken> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(token);
    }

    private ResponseEntity<UserProfile> signedIn(SessionTokens tokens) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).headers(cookies.issue(tokens))
                .header("Referrer-Policy", "no-referrer").body(tokens.user());
    }
}
