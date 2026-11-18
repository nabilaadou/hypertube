package com.hypertube.auth.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hypertube.auth.tokens")
public record TokenProperties(Duration accessTtl, Duration slidingTtl, Duration absoluteTtl,
                              Duration refreshGrace, boolean secureCookies) {
    public TokenProperties {
        if (accessTtl == null || slidingTtl == null || absoluteTtl == null || refreshGrace == null
                || accessTtl.compareTo(Duration.ofSeconds(1)) < 0 || slidingTtl.compareTo(accessTtl) < 0
                || absoluteTtl.compareTo(slidingTtl) < 0 || refreshGrace.isNegative()
                || refreshGrace.compareTo(Duration.ofSeconds(5)) > 0) {
            throw new IllegalArgumentException("Require 1s <= access-ttl <= sliding-ttl <= absolute-ttl and 0s <= refresh-grace <= 5s");
        }
    }
}
