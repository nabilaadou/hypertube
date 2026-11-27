package com.hypertube.auth.schema;

public record UserProfile(long id, String provider, String providerUserId, String displayName, String email,
                          String username, String firstName, String lastName) {
    public UserProfile(long id, String provider, String providerUserId, String displayName, String email) {
        this(id, provider, providerUserId, displayName, email, null, null, null);
    }
}
