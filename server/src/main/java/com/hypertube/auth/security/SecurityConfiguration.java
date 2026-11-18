package com.hypertube.auth.security;

import java.time.Clock;
import java.util.function.Supplier;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

import com.hypertube.auth.service.TokenService;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({TokenProperties.class, JwtProperties.class})
public class SecurityConfiguration {
    @Bean
    Clock tokenClock() {
        return Clock.systemUTC();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokens, TokenProperties properties,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors)
            throws Exception {
        var csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokens.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Strict")
                .secure(properties.secureCookies()));

        var xorHandler = new XorCsrfTokenRequestAttributeHandler();
        var headerOnlyCsrf = new CsrfTokenRequestHandler() {
            @Override
            public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> token) {
                xorHandler.handle(request, response, token);
            }
            @Override
            public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) {
                return request.getHeader(token.getHeaderName()) == null ? null : xorHandler.resolveCsrfTokenValue(request, token);
            }
        };

        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens).csrfTokenRequestHandler(headerOnlyCsrf)
                        .requireCsrfProtectionMatcher(request -> {
                            String path = request.getRequestURI().substring(request.getContextPath().length());
                            return java.util.Set.of("/api/auth/refresh", "/api/auth/logout").contains(path)
                                    && !java.util.Set.of("GET", "HEAD", "OPTIONS", "TRACE").contains(request.getMethod());
                        }))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/csrf").permitAll()
                        .requestMatchers("/internal/auth/login-handoffs", "/api/auth/login-handoffs/complete",
                                "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) -> errors.resolveException(request, response, null, exception))
                        .accessDeniedHandler((request, response, exception) -> errors.resolveException(request, response, null, exception)))
                .addFilterBefore(new AccessTokenAuthenticationFilter(tokens), AnonymousAuthenticationFilter.class)
                .build();
    }
}
