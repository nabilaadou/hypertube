package com.hypertube.auth.security;

import java.io.IOException;
import java.util.Collections;
import com.hypertube.auth.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    public AccessTokenAuthenticationFilter(TokenService tokens) { this.tokens = tokens; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var headers = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if (headers.size() == 1) {
            String header = headers.getFirst();
            if (header.length() <= 4103 && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                tokens.authenticate(header.substring(7)).ifPresent(principal -> {
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                            principal.authorities().stream().map(SimpleGrantedAuthority::new).toList()));
                    SecurityContextHolder.setContext(context);
                });
            }
        }
        chain.doFilter(request, response);
    }
}
