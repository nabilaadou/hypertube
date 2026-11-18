package com.hypertube.auth.schema;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(UUID id, String deviceInfo, String ipAddress, Instant lastUsedAt, boolean isCurrent) {}
