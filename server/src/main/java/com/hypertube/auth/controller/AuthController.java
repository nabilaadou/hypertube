package com.hypertube.auth.controller;

import java.util.UUID;

import com.hypertube.auth.schema.OAuthIdentity;
import com.hypertube.auth.schema.UserProfile;
import com.hypertube.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class AuthController {
    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
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
        var profile = auth.finishLogin(code, proof);
        if (profile.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore())
                    .header("Referrer-Policy", "no-referrer").build();
        }

        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").body(profile.get());
    }
}
