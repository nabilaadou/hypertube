package com.hypertube.auth.security;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

public record SessionPrincipal(long userId, UUID sessionId, List<String> authorities) implements Principal {
    public SessionPrincipal { authorities = List.copyOf(authorities); }
    @Override
    public String getName() { return Long.toString(userId); }
}
