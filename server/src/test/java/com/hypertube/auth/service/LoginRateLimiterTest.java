package com.hypertube.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {
    @Test
    void usernameAndAddressLimitsExpireAfterCooldown() {
        var clock = new TestClock();
        var limiter = new LoginRateLimiter(clock);
        for (int i = 0; i < 5; i++) assertThat(limiter.authenticate("name", "ip" + i, () -> false)).isFalse();
        assertThatThrownBy(() -> limiter.authenticate("name", "other", () -> true)).isInstanceOf(ResponseStatusException.class);
        clock.advance(Duration.ofMinutes(15));
        assertThat(limiter.authenticate("name", "other", () -> true)).isTrue();
        for (int i = 0; i < 20; i++) assertThat(limiter.authenticate("user" + i, "shared", () -> false)).isFalse();
        assertThatThrownBy(() -> limiter.authenticate("another", "shared", () -> true)).isInstanceOf(ResponseStatusException.class);
        clock.advance(Duration.ofMinutes(15));
        assertThat(limiter.authenticate("another", "shared", () -> true)).isTrue();
    }

    @Test
    void simultaneousFailuresCannotBypassUsernameLimit() throws Exception {
        var limiter = new LoginRateLimiter(new TestClock());
        var checked = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(12)) {
            var futures = java.util.stream.IntStream.range(0, 12).mapToObj(i -> executor.submit(() -> {
                try { limiter.authenticate("same", "address" + i, () -> { checked.incrementAndGet(); return false; }); }
                catch (ResponseStatusException expected) { assertThat(expected.getStatusCode().value()).isEqualTo(429); }
            })).toList();
            for (var future : futures) future.get();
        }
        assertThat(checked.get()).isEqualTo(5);
    }

    static class TestClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
}
