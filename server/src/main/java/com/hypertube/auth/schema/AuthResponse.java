package com.hypertube.auth.schema;

import java.time.Instant;

public record AuthResponse(long expiresIn, Instant expiresAt, UserProfile user) {
    @Override
    public String toString() { return "AuthResponse[userId=" + user.id() + ", expiresAt=" + expiresAt + "]"; }
}
