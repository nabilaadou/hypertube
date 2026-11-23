package com.hypertube.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final Map<String, Attempts> usernames = new HashMap<>();
    private final Map<String, Attempts> addresses = new HashMap<>();
    private final Clock clock;

    public LoginRateLimiter(Clock clock) { this.clock = clock; }

    public synchronized boolean authenticate(String username, String address, java.util.function.BooleanSupplier verify) {
        Instant now = clock.instant();
        usernames.values().removeIf(attempt -> attempt.expired(now));
        addresses.values().removeIf(attempt -> attempt.expired(now));
        if (blocked(usernames.get(username), now) || blocked(addresses.get(address), now)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Try again later.");
        }
        if (verify.getAsBoolean()) {
            usernames.remove(username);
            return true;
        }
        fail(usernames, username, 5, now);
        fail(addresses, address, 20, now);
        return false;
    }

    private static boolean blocked(Attempts attempts, Instant now) {
        return attempts != null && attempts.blockedUntil != null && now.isBefore(attempts.blockedUntil);
    }

    private static void fail(Map<String, Attempts> attempts, String key, int limit, Instant now) {
        Attempts entry = attempts.computeIfAbsent(key, ignored -> new Attempts(now));
        if (++entry.count >= limit) entry.blockedUntil = now.plus(WINDOW);
    }

    private static final class Attempts {
        private final Instant started;
        private int count;
        private Instant blockedUntil;
        private Attempts(Instant started) { this.started = started; }
        private boolean expired(Instant now) {
            return blockedUntil == null ? !now.isBefore(started.plus(WINDOW)) : !now.isBefore(blockedUntil);
        }
    }
}
