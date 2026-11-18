package com.hypertube.auth.security;

import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookies {
    public static final String ACCESS = "hypertube_access";
    public static final String REFRESH = "hypertube_refresh";
    private final TokenProperties properties;
    public AuthCookies(TokenProperties properties) { this.properties = properties; }

    public HttpHeaders issue(AuthTokens tokens) {
        var headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH, tokens.refreshToken(), "/api/auth", tokens.refreshMaxAge()));
        headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS, tokens.accessToken(), "/", Duration.ofSeconds(tokens.response().expiresIn())));
        return headers;
    }

    public String clearLoginProof() {
        return cookie("hypertube_login_proof", "", "/api/auth/login-handoffs/complete", Duration.ZERO);
    }

    public HttpHeaders clear() {
        var headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH, "", "/api/auth", Duration.ZERO));
        headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS, "", "/", Duration.ZERO));
        return headers;
    }

    private String cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(properties.secureCookies())
                .sameSite("Strict").path(path).maxAge(maxAge).build().toString();
    }
}
