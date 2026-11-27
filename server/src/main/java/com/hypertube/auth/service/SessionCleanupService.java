package com.hypertube.auth.service;

import java.time.Clock;
import com.hypertube.auth.repository.RevokedSessionRepository;
import com.hypertube.auth.repository.UserSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionCleanupService {
    private static final String CLEANUP_INTERVAL =
            "#{@'hypertube.auth-com.hypertube.auth.security.AuthProperties'.cleanupInterval()}";
    private final UserSessionRepository sessions;
    private final RevokedSessionRepository revocations;
    private final Clock clock;
    private final JdbcTemplate jdbc;

    public SessionCleanupService(UserSessionRepository sessions, RevokedSessionRepository revocations, Clock clock, JdbcTemplate jdbc) {
        this.sessions = sessions;
        this.revocations = revocations;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = CLEANUP_INTERVAL, initialDelayString = CLEANUP_INTERVAL)
    @Transactional
    public void cleanup() {
        var now = clock.instant();
        sessions.deleteExpiredAndRevoked(now);
        revocations.deleteExpired(now);
        jdbc.update("DELETE FROM password_reset_tokens WHERE expires_at <= ?", java.sql.Timestamp.from(now));
    }
}
