package com.hypertube.auth.security;

import java.time.Duration;
import java.time.Instant;
import com.hypertube.auth.schema.UserProfile;

public record AuthTokens(String accessToken, String refreshToken, Duration accessMaxAge,
                         Duration refreshMaxAge, Instant accessExpiresAt, UserProfile user) {
    @Override
    public String toString() { return "AuthTokens[userId=" + user.id() + "]"; }
}
