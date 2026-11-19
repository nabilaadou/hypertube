package com.hypertube.auth.schema;

public record UserProfile(long id, String provider, String providerUserId, String displayName, String email) {}
