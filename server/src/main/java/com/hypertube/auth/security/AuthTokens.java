package com.hypertube.auth.security;

import java.time.Duration;
import com.hypertube.auth.schema.AuthResponse;

public record AuthTokens(AuthResponse response, String accessToken, String refreshToken, Duration refreshMaxAge) {
    @Override
    public String toString() { return "AuthTokens[userId=" + response.user().id() + "]"; }
}
