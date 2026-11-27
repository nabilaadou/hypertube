package com.hypertube.auth.controller;

import java.util.UUID;
import com.hypertube.auth.schema.AuthResponse;
import com.hypertube.auth.schema.OAuthIdentity;
import com.hypertube.auth.schema.OAuthProfileRequest;
import com.hypertube.auth.schema.UserProfile;
import com.hypertube.auth.schema.LoginRequest;
import com.hypertube.auth.schema.RegistrationRequest;
import com.hypertube.auth.schema.PasswordResetRequest;
import com.hypertube.auth.schema.PasswordResetConfirmation;
import com.hypertube.auth.security.AuthCookies;
import com.hypertube.auth.security.AuthTokens;
import com.hypertube.auth.security.SessionPrincipal;
import com.hypertube.auth.service.AuthService;
import com.hypertube.auth.service.TokenService;
import com.hypertube.auth.service.LocalAuthService;
import com.hypertube.auth.service.PasswordResetService;
import com.hypertube.auth.service.OAuthFlowService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
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
    private final TokenService tokens;
    private final AuthCookies cookies;
    private final LocalAuthService local;
    private final PasswordResetService passwordReset;
    private final OAuthFlowService oauth;

    AuthController(AuthService auth, TokenService tokens, AuthCookies cookies, LocalAuthService local, PasswordResetService passwordReset, OAuthFlowService oauth) {
        this.auth = auth;
        this.tokens = tokens;
        this.cookies = cookies;
        this.local = local;
        this.passwordReset = passwordReset;
        this.oauth = oauth;
    }

    @PostMapping("/api/auth/password-reset/request")
    ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordReset.request(request.email());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/api/auth/password-reset/confirm")
    ResponseEntity<AuthResponse> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmation request) {
        return localSignedIn(passwordReset.confirm(request));
    }

    @PostMapping("/api/auth/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegistrationRequest request) {
        return localSignedIn(local.register(request));
    }

    @PostMapping("/api/auth/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return localSignedIn(local.login(request, servletRequest.getRemoteAddr()));
    }

    private ResponseEntity<AuthResponse> localSignedIn(AuthTokens result) {
        return signedIn(result).body(new AuthResponse(result.accessMaxAge().toSeconds(), result.accessExpiresAt(), result.user()));
    }

    @PostMapping("/internal/auth/login-handoffs")
    ResponseEntity<String> createHandoff(@RequestHeader(value = "Authorization", defaultValue = "") String authorization,
                                         @Valid @RequestBody OAuthIdentity identity) {
        if (!auth.isHandoffAuthorized(authorization)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.startLogin(identity).toString());
    }

    @PostMapping("/api/auth/login-handoffs/complete")
    ResponseEntity<?> completeHandoff(@RequestParam UUID code,
            @CookieValue(value = "hypertube_login_proof", defaultValue = "") String proof) {
        return oauthResponse(oauth.begin(code, proof), proof);
    }

    @PostMapping("/api/auth/login-handoffs/profile")
    ResponseEntity<?> completeOAuthProfile(@RequestParam UUID code,
            @CookieValue(value = "hypertube_login_proof", defaultValue = "") String proof,
            @Valid @RequestBody OAuthProfileRequest request) {
        return oauthResponse(oauth.profile(code, proof, request), proof);
    }

    @PostMapping("/api/auth/login-handoffs/link")
    ResponseEntity<?> linkOAuthAccount(@RequestParam UUID code,
            @CookieValue(value = "hypertube_login_proof", defaultValue = "") String proof,
            @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return oauthResponse(oauth.link(code, proof, request, servletRequest.getRemoteAddr()), proof);
    }

    private ResponseEntity<?> oauthResponse(OAuthFlowService.Outcome result, String proof) {
        if (result.status() == OAuthFlowService.Status.AUTHENTICATED) {
            var session = result.tokens();
            return signedIn(session).header(HttpHeaders.SET_COOKIE, cookies.clearLoginProof())
                    .body(new AuthResponse(session.accessMaxAge().toSeconds(), session.accessExpiresAt(), session.user()));
        }
        long seconds = result.status() == OAuthFlowService.Status.PROFILE_REQUIRED ? 900 : 120;
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header(HttpHeaders.SET_COOKIE, cookies.loginProof(proof, java.time.Duration.ofSeconds(seconds)))
                .body(new PendingOAuth(result.status().name().toLowerCase(java.util.Locale.ROOT), result.expiresAt()));
    }

    private record PendingOAuth(String status, java.time.Instant expiresAt) {}

    @PostMapping("/api/auth/refresh")
    ResponseEntity<AuthResponse> refresh(@CookieValue(value = AuthCookies.REFRESH, defaultValue = "") String refreshToken) {
        AuthTokens result = tokens.refresh(refreshToken);
        return signedIn(result).body(new AuthResponse(result.accessMaxAge().toSeconds(), result.accessExpiresAt(), result.user()));
    }

    @PostMapping("/api/auth/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal SessionPrincipal principal,
            @CookieValue(value = AuthCookies.REFRESH, defaultValue = "") String refreshToken) {
        tokens.logout(principal, refreshToken);
        return signedOut();
    }

    @GetMapping("/api/auth/me")
    ResponseEntity<UserProfile> currentUser(@AuthenticationPrincipal SessionPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tokens.currentUser(principal));
    }

    @GetMapping("/api/auth/csrf")
    ResponseEntity<CsrfToken> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(token);
    }

    private ResponseEntity.BodyBuilder signedIn(AuthTokens result) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).headers(cookies.issue(result))
                .header("Referrer-Policy", "no-referrer");
    }

    private ResponseEntity<Void> signedOut() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).headers(cookies.clear()).build();
    }
}
