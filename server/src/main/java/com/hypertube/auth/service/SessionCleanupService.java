package com.hypertube.auth.service;

import java.time.Clock;
import com.hypertube.auth.repository.RevokedSessionRepository;
import com.hypertube.auth.repository.UserSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionCleanupService {
    private final UserSessionRepository sessions;
    private final RevokedSessionRepository revocations;
    private final Clock clock;

    public SessionCleanupService(UserSessionRepository sessions, RevokedSessionRepository revocations, Clock clock) {
        this.sessions = sessions;
        this.revocations = revocations;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${hypertube.auth.cleanup-interval:PT15M}",
            initialDelayString = "${hypertube.auth.cleanup-interval:PT15M}")
    @Transactional
    public void cleanup() {
        var now = clock.instant();
        sessions.deleteExpiredAndRevoked(now);
        revocations.deleteExpired(now);
    }
}
