package com.hypertube.auth.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hypertube.auth.session")
public record SessionProperties(Duration accessTtl, Duration refreshTtl, boolean secureCookies) {
    public SessionProperties {
        if (accessTtl == null || refreshTtl == null || accessTtl.isNegative() || accessTtl.isZero()
                || refreshTtl.compareTo(accessTtl) < 0) {
            throw new IllegalArgumentException("Session lifetimes must be positive and refresh-ttl must be at least access-ttl");
        }
    }
}
