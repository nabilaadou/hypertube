package com.hypertube.auth.security;

import java.security.Principal;
import java.util.UUID;

public record SessionPrincipal(long userId, UUID sessionId) implements Principal {
    @Override
    public String getName() { return Long.toString(userId); }
}
