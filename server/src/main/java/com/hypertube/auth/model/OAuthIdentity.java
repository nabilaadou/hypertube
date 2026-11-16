package com.hypertube.auth.model;

public record OAuthIdentity(String provider, String providerUserId, String displayName, String email, String proofHash) {}
