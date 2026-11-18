package com.hypertube.auth.security;

import java.io.IOException;
import java.util.List;

import com.hypertube.auth.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {
    private final SessionService sessions;

    public AccessTokenAuthenticationFilter(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var cookie = WebUtils.getCookie(request, SessionCookies.ACCESS);
        if (cookie != null) {
            sessions.authenticate(cookie.getValue()).ifPresent(user -> {
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
                SecurityContextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}
