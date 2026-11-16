package com.hypertube.auth.model;

public record UserProfile(long id, String provider, String providerUserId, String displayName, String email) {}
