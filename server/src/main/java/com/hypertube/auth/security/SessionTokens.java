package com.hypertube.auth.security;

import java.time.Duration;

import com.hypertube.auth.schema.UserProfile;

public record SessionTokens(UserProfile user, String accessToken, String refreshToken,
                            Duration accessMaxAge, Duration refreshMaxAge) {
    @Override
    public String toString() {
        return "SessionTokens[userId=" + user.id() + "]";
    }
}
