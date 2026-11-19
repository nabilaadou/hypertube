package com.hypertube.auth.service;

import java.time.Clock;
import com.hypertube.auth.repository.RevokedSessionRepository;
import com.hypertube.auth.repository.UserSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionCleanupService {
    private static final String CLEANUP_INTERVAL =
            "#{@'hypertube.auth-com.hypertube.auth.security.AuthProperties'.cleanupInterval()}";
    private final UserSessionRepository sessions;
    private final RevokedSessionRepository revocations;
    private final Clock clock;

    public SessionCleanupService(UserSessionRepository sessions, RevokedSessionRepository revocations, Clock clock) {
        this.sessions = sessions;
        this.revocations = revocations;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = CLEANUP_INTERVAL, initialDelayString = CLEANUP_INTERVAL)
    @Transactional
    public void cleanup() {
        var now = clock.instant();
        sessions.deleteExpiredAndRevoked(now);
        revocations.deleteExpired(now);
    }
}
