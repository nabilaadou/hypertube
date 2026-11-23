package com.hypertube.auth.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.hypertube.auth.exception.AuthException;
import com.hypertube.auth.schema.OAuthIdentity;
import com.hypertube.auth.schema.UserProfile;
import com.hypertube.auth.security.AuthCookies;
import com.hypertube.auth.security.AuthTokens;
import com.hypertube.auth.security.SecurityConfiguration;
import com.hypertube.auth.security.SessionPrincipal;
import com.hypertube.auth.service.AuthService;
import com.hypertube.auth.service.TokenService;
import com.hypertube.auth.service.LocalAuthService;
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

@WebMvcTest(controllers = AuthController.class, properties = "hypertube.auth.secure-cookies=true")
@Import({SecurityConfiguration.class, AuthCookies.class, AuthControllerTest.ProtectedMutation.class})
class AuthControllerTest {
    private static final UUID CODE = UUID.fromString("7d87e5dc-cf71-421f-9e7c-853155898d67");
    private static final String PROOF = "a".repeat(64);
    private static final UserProfile USER = new UserProfile(17L, "google", "user-123", "Test User", "test@example.com");
    private static final SessionPrincipal PRINCIPAL = new SessionPrincipal(17L, CODE);
    private static final AuthTokens TOKENS = new AuthTokens("signed.jwt.value", "b".repeat(43), Duration.ofMinutes(10),
            Duration.ofDays(30), Instant.parse("2026-11-18T12:10:00Z"), USER);

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private TokenService tokens;
    @MockitoBean private LocalAuthService local;

    @Test
    void localEndpointsRequireCsrfAndIssueSessionCookies() throws Exception {
        String registration = """
                {"username":"Sultan","email":"sultan@example.com","firstName":"Test","lastName":"User","password":"a-strong-password"}
                """;
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isForbidden());
        when(local.register(any())).thenReturn(TOKENS);
        mvc.perform(withCsrf("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isOk()).andExpect(cookie().httpOnly(AuthCookies.ACCESS, true))
                .andExpect(cookie().httpOnly(AuthCookies.REFRESH, true)).andExpect(jsonPath("$.accessToken").doesNotExist());
        when(local.login(any(), any())).thenReturn(TOKENS);
        mvc.perform(withCsrf("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Sultan\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isOk()).andExpect(cookie().exists(AuthCookies.ACCESS));
        mvc.perform(withCsrf("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

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
    void issuesHttpOnlyCredentialsWithoutReturningTokensInJson() throws Exception {
        when(tokens.completeLogin(CODE, PROOF)).thenReturn(TOKENS);
        var result = mvc.perform(post("/api/auth/login-handoffs/complete").param("code", CODE.toString())
                        .cookie(new Cookie("hypertube_login_proof", PROOF)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.tokenType").doesNotExist()).andExpect(jsonPath("$.expiresIn").value(600))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-18T12:10:00Z"))
                .andExpect(jsonPath("$.user.id").value(17)).andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().value(AuthCookies.REFRESH, TOKENS.refreshToken()))
                .andExpect(cookie().httpOnly(AuthCookies.REFRESH, true)).andExpect(cookie().secure(AuthCookies.REFRESH, true))
                .andExpect(cookie().path(AuthCookies.REFRESH, "/api/auth")).andExpect(cookie().maxAge(AuthCookies.REFRESH, 2592000))
                .andExpect(cookie().value(AuthCookies.ACCESS, TOKENS.accessToken()))
                .andExpect(cookie().httpOnly(AuthCookies.ACCESS, true)).andExpect(cookie().secure(AuthCookies.ACCESS, true))
                .andExpect(cookie().path(AuthCookies.ACCESS, "/")).andExpect(cookie().maxAge(AuthCookies.ACCESS, 600)).andExpect(cookie().maxAge("hypertube_login_proof", 0))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        assertThat(result.getResponse().getCookie(AuthCookies.REFRESH).getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(result.getResponse().getCookie(AuthCookies.ACCESS).getAttribute("SameSite")).isEqualTo("Strict");
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
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(cookie().value(AuthCookies.REFRESH, TOKENS.refreshToken()))
                .andExpect(cookie().value(AuthCookies.ACCESS, TOKENS.accessToken()))
                .andExpect(cookie().doesNotExist("hypertube_login_proof"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void centralizedRefreshFailureClearsCookie() throws Exception {
        when(tokens.refresh("")).thenThrow(new AuthException("invalid_refresh", "Invalid refresh", true));
        mvc.perform(withCsrf("/api/auth/refresh")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_refresh"))
                .andExpect(cookie().maxAge(AuthCookies.REFRESH, 0))
                .andExpect(cookie().maxAge(AuthCookies.ACCESS, 0));
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
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge(AuthCookies.REFRESH, 0))
                .andExpect(cookie().maxAge(AuthCookies.ACCESS, 0));
        verify(tokens).logout(null, "refresh");
    }

    @Test
    void authenticatesOnlyAccessCookieAndReturnsCurrentUser() throws Exception {
        when(tokens.authenticate("valid-jwt")).thenReturn(Optional.of(PRINCIPAL));
        when(tokens.currentUser(PRINCIPAL)).thenReturn(USER);
        mvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookies.ACCESS, "valid-jwt")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(17));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer valid-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void removedSessionManagementEndpointsAreNotExposed() throws Exception {
        when(tokens.authenticate("valid-jwt")).thenReturn(Optional.of(PRINCIPAL));
        mvc.perform(withCsrf("/api/auth/logout-all").cookie(new Cookie(AuthCookies.ACCESS, "valid-jwt")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/auth/sessions").cookie(new Cookie(AuthCookies.ACCESS, "valid-jwt")))
                .andExpect(status().isNotFound());
    }

    @Test
    void exposesCsrfTokenWithoutServletSession() throws Exception {
        var result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN")).andExpect(cookie().exists("XSRF-TOKEN")).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void rejectsDuplicateAndOversizedAccessCookies() throws Exception {
        mvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookies.ACCESS, "first"), new Cookie(AuthCookies.ACCESS, "second")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookies.ACCESS, "a".repeat(4097))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(tokens);
    }

    @Test
    void protectedMutationsRequireCsrfEvenWithValidAccessCookie() throws Exception {
        when(tokens.authenticate("valid-jwt")).thenReturn(Optional.of(PRINCIPAL));
        mvc.perform(post("/api/test/mutation").cookie(new Cookie(AuthCookies.ACCESS, "valid-jwt")))
                .andExpect(status().isForbidden());
        mvc.perform(withCsrf("/api/test/mutation").cookie(new Cookie(AuthCookies.ACCESS, "valid-jwt")))
                .andExpect(status().isNoContent());
    }

    @org.springframework.web.bind.annotation.RestController
    static class ProtectedMutation {
        @org.springframework.web.bind.annotation.PostMapping("/api/test/mutation")
        org.springframework.http.ResponseEntity<Void> mutate() {
            return org.springframework.http.ResponseEntity.noContent().build();
        }
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
