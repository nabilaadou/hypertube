package com.hypertube.auth.controller;

import java.time.Duration;
import java.util.UUID;
import com.hypertube.auth.schema.EmailChangeConfirmation;
import com.hypertube.auth.schema.ProfileUpdate;
import com.hypertube.auth.security.AuthCookies;
import com.hypertube.auth.security.SessionPrincipal;
import com.hypertube.auth.service.AuthService;
import com.hypertube.auth.service.EmailChangeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class ProfileController {
    private final EmailChangeService changes;
    private final AuthService auth;
    private final AuthCookies cookies;
    ProfileController(EmailChangeService changes, AuthService auth, AuthCookies cookies) {
        this.changes = changes; this.auth = auth; this.cookies = cookies;
    }

    @PatchMapping("/api/users/{id}")
    ResponseEntity<EmailChangeService.Result> update(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable long id,
            @Valid @RequestBody ProfileUpdate request, HttpServletRequest servlet,
            @CookieValue(value = "hypertube_reauth", defaultValue = "") String secret) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(changes.update(principal, id, request, servlet.getRemoteAddr(), secret));
    }

    @PostMapping("/api/auth/email-change/confirm")
    ResponseEntity<Void> confirm(@Valid @RequestBody EmailChangeConfirmation request) {
        changes.confirm(request.token());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/api/auth/reauth/start")
    ResponseEntity<Started> start(@AuthenticationPrincipal SessionPrincipal principal) {
        var challenge = changes.start(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookies.reauth(challenge.secret(), Duration.ofMinutes(2)))
                .body(new Started(challenge.id()));
    }
    private record Started(UUID challenge) {}

    @PostMapping("/internal/auth/reauth/callback")
    ResponseEntity<Void> callback(@RequestHeader(value = "Authorization", defaultValue = "") String authorization,
                                   @Valid @RequestBody Callback request) {
        if (!auth.isHandoffAuthorized(authorization)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        changes.providerCallback(request.challenge(), request.secret(), request.provider(), request.providerUserId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    private record Callback(@NotNull UUID challenge, @NotBlank @Size(max = 512) String secret,
                            @NotNull @Pattern(regexp = "42|google") String provider,
                            @NotBlank @Size(max = 128) String providerUserId) {}
}
