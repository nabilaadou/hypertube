package com.hypertube.auth.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hypertube.auth.tokens")
public record TokenProperties(Duration accessTtl, Duration refreshTtl, boolean secureCookies) {
    public TokenProperties {
        if (accessTtl == null || refreshTtl == null || accessTtl.compareTo(Duration.ofSeconds(1)) < 0
                || refreshTtl.compareTo(accessTtl) < 0) {
            throw new IllegalArgumentException("Require 1s <= access-ttl <= refresh-ttl");
        }
    }
}
