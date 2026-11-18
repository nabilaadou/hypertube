package com.hypertube.auth.security;

import java.io.IOException;
import java.util.Collections;
import com.hypertube.auth.service.TokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    public AccessTokenAuthenticationFilter(TokenService tokens) { this.tokens = tokens; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String accessToken = accessToken(request);
        if (accessToken != null && !accessToken.isEmpty() && accessToken.length() <= 4096) {
            tokens.authenticate(accessToken).ifPresent(principal -> {
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                        Collections.emptyList()));
                SecurityContextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }

    private String accessToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        String value = null;
        for (Cookie cookie : request.getCookies()) {
            if (AuthCookies.ACCESS.equals(cookie.getName())) {
                if (value != null) {
                    return null;
                }
                value = cookie.getValue();
            }
        }
        return value;
    }
}
