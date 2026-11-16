package com.hypertube.auth.controller;

import java.net.URI;
import java.util.UUID;

import com.hypertube.auth.model.OAuthIdentity;
import com.hypertube.auth.model.UserProfile;
import com.hypertube.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
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
    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/internal/auth/identities")
    ResponseEntity<String> receiveIdentity(@RequestHeader(value = "Authorization", defaultValue = "") String authorization,
                                           @Valid @RequestBody OAuthIdentity identity, Errors errors) {
        if (!auth.isHandoffAuthorized(authorization)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (errors.hasErrors()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        UUID code = auth.startLogin(identity);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(code.toString());
    }

    @GetMapping("/api/auth/complete")
    ResponseEntity<Void> complete(@RequestParam UUID code, HttpServletRequest request,
                                  @CookieValue(value = "hypertube_login_proof", defaultValue = "") String proof) {
        var response = ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer");
        var userId = auth.finishLogin(code, proof);
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
    ResponseEntity<UserProfile> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SESSION_USER) instanceof Long id) {
            var profile = auth.findProfile(id);
            if (profile.isPresent()) {
                return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profile.get());
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore()).build();
    }
}
