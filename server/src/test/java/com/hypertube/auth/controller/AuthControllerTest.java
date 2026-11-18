package com.hypertube.auth.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.hypertube.auth.exception.AuthException;
import com.hypertube.auth.schema.AuthResponse;
import com.hypertube.auth.schema.OAuthIdentity;
import com.hypertube.auth.schema.UserProfile;
import com.hypertube.auth.security.AuthCookies;
import com.hypertube.auth.security.AuthTokens;
import com.hypertube.auth.security.SecurityConfiguration;
import com.hypertube.auth.security.SessionPrincipal;
import com.hypertube.auth.service.AuthService;
import com.hypertube.auth.service.TokenService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class, properties = "hypertube.auth.tokens.secure-cookies=true")
@Import({SecurityConfiguration.class, AuthCookies.class})
class AuthControllerTest {
    private static final UUID CODE = UUID.fromString("7d87e5dc-cf71-421f-9e7c-853155898d67");
    private static final String PROOF = "a".repeat(64);
    private static final UserProfile USER = new UserProfile(17L, "google", "user-123", "Test User", "test@example.com");
    private static final SessionPrincipal PRINCIPAL = new SessionPrincipal(17L, CODE, List.of("ROLE_USER"));
    private static final AuthTokens TOKENS = new AuthTokens(new AuthResponse("signed.jwt.value", "Bearer", 600,
            Instant.parse("2026-11-18T12:10:00Z"), USER), "b".repeat(43), Duration.ofDays(7));

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private TokenService tokens;

    @Test
    void createsAuthorizedHandoff() throws Exception {
        when(auth.isHandoffAuthorized("Bearer handoff-secret")).thenReturn(true);
        when(auth.startLogin(any(OAuthIdentity.class))).thenReturn(CODE);
        mvc.perform(post("/internal/auth/login-handoffs").header("Authorization", "Bearer handoff-secret")
                        .contentType(MediaType.APPLICATION_JSON).content(identity("google")))
                .andExpect(status().isOk()).andExpect(content().string(CODE.toString()));
    }

    @Test
    void rejectsUnauthorizedOrInvalidIdentity() throws Exception {
        mvc.perform(post("/internal/auth/login-handoffs").contentType(MediaType.APPLICATION_JSON).content(identity("google")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/auth/login-handoffs").contentType(MediaType.APPLICATION_JSON).content(identity("unsupported")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsJwtInJsonAndOnlyRefreshCredentialInCookie() throws Exception {
        when(tokens.completeLogin(CODE, PROOF)).thenReturn(TOKENS);
        var result = mvc.perform(post("/api/auth/login-handoffs/complete").param("code", CODE.toString())
                        .cookie(new Cookie("hypertube_login_proof", PROOF)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").value("signed.jwt.value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresIn").value(600))
                .andExpect(jsonPath("$.user.id").value(17)).andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().value(AuthCookies.REFRESH, TOKENS.refreshToken()))
                .andExpect(cookie().httpOnly(AuthCookies.REFRESH, true)).andExpect(cookie().secure(AuthCookies.REFRESH, true))
                .andExpect(cookie().path(AuthCookies.REFRESH, "/api/auth")).andExpect(cookie().maxAge(AuthCookies.REFRESH, 604800))
                .andExpect(cookie().maxAge("hypertube_access", 0)).andExpect(cookie().maxAge("hypertube_login_proof", 0))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        assertThat(result.getResponse().getCookie(AuthCookies.REFRESH).getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void centralizedHandoffFailureDoesNotClearExistingRefresh() throws Exception {
        when(tokens.completeLogin(CODE, PROOF))
                .thenThrow(new AuthException("invalid_handoff", "Invalid handoff", false));
        mvc.perform(post("/api/auth/login-handoffs/complete").param("code", CODE.toString())
                        .cookie(new Cookie("hypertube_login_proof", PROOF)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("invalid_handoff"))
                .andExpect(cookie().doesNotExist(AuthCookies.REFRESH));
    }

    @Test
    void refreshesWithoutAnAccessToken() throws Exception {
        when(tokens.refresh("refresh")).thenReturn(TOKENS);
        mvc.perform(withCsrf("/api/auth/refresh").cookie(new Cookie(AuthCookies.REFRESH, "refresh")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").value("signed.jwt.value"))
                .andExpect(cookie().value(AuthCookies.REFRESH, TOKENS.refreshToken()));
    }

    @Test
    void graceResponseDoesNotOverwriteRefreshCookie() throws Exception {
        when(tokens.refresh("old")).thenReturn(new AuthTokens(TOKENS.response(), null, Duration.ofDays(7)));
        mvc.perform(withCsrf("/api/auth/refresh").cookie(new Cookie(AuthCookies.REFRESH, "old")))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void centralizedRefreshFailureClearsCookie() throws Exception {
        when(tokens.refresh("")).thenThrow(new AuthException("invalid_refresh", "Invalid refresh", true));
        mvc.perform(withCsrf("/api/auth/refresh")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_refresh"))
                .andExpect(cookie().maxAge(AuthCookies.REFRESH, 0));
    }

    @Test
    void refreshAndLogoutRequireCsrf() throws Exception {
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        mvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());
        verifyNoInteractions(tokens);
    }

    @Test
    void currentLogoutIsIdempotentAndClearsCookie() throws Exception {
        mvc.perform(withCsrf("/api/auth/logout").cookie(new Cookie(AuthCookies.REFRESH, "refresh")))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge(AuthCookies.REFRESH, 0));
        verify(tokens).logout(null, "refresh");
    }

    @Test
    void authenticatesOnlyBearerHeaderAndReturnsCurrentUser() throws Exception {
        when(tokens.authenticate("valid-jwt")).thenReturn(Optional.of(PRINCIPAL));
        when(tokens.currentUser(PRINCIPAL)).thenReturn(USER);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer valid-jwt"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(17));
        mvc.perform(get("/api/auth/me").cookie(new Cookie("hypertube_access", "valid-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void removedSessionManagementEndpointsAreNotExposed() throws Exception {
        when(tokens.authenticate("valid-jwt")).thenReturn(Optional.of(PRINCIPAL));
        mvc.perform(post("/api/auth/logout-all").header("Authorization", "Bearer valid-jwt"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/auth/sessions").header("Authorization", "Bearer valid-jwt"))
                .andExpect(status().isNotFound());
    }

    @Test
    void exposesCsrfTokenWithoutServletSession() throws Exception {
        var result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN")).andExpect(cookie().exists("XSRF-TOKEN")).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    private MockHttpServletRequestBuilder withCsrf(String path) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse();
        String csrf = JsonPath.read(response.getContentAsString(), "$.token");
        return post(path).cookie(response.getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", csrf);
    }

    private String identity(String provider) {
        return """
                {"provider":"%s","providerUserId":"user-123","proofHash":"%s"}
                """.formatted(provider, "b".repeat(64));
    }
}
