package com.hypertube.auth.security;

import java.time.Clock;
import java.time.ZoneOffset;
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
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfiguration {
    @Bean
    Clock tokenClock() {
        return Clock.tickSeconds(ZoneOffset.UTC);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokens, AuthProperties properties,
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
                        // These handoffs use the internal shared secret and browser proof, respectively.
                        .ignoringRequestMatchers("/internal/auth/login-handoffs", "/api/auth/login-handoffs/complete"))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/csrf").permitAll()
                        .requestMatchers("/internal/auth/login-handoffs", "/api/auth/login-handoffs/complete", "/api/auth/register", "/api/auth/login", "/api/auth/password-reset/request", "/api/auth/password-reset/confirm", "/api/auth/login-handoffs/profile", "/api/auth/login-handoffs/link",
                                "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) -> errors.resolveException(request, response, null, exception))
                        .accessDeniedHandler((request, response, exception) -> errors.resolveException(request, response, null, exception)))
                .addFilterBefore(new AccessTokenAuthenticationFilter(tokens), AnonymousAuthenticationFilter.class)
                .build();
    }
}
