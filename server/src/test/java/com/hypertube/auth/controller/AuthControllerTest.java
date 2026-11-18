package com.hypertube.auth.controller;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import com.hypertube.auth.schema.OAuthIdentity;
import com.hypertube.auth.schema.UserProfile;
import com.hypertube.auth.service.AuthService;
import com.hypertube.auth.service.TokenService;
import com.hypertube.auth.security.SecurityConfiguration;
import com.hypertube.auth.security.AuthCookies;
import com.hypertube.auth.security.AuthTokens;
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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class, properties = "hypertube.auth.tokens.secure-cookies=true")
@Import({SecurityConfiguration.class, AuthCookies.class})
class AuthControllerTest {
    private static final UUID CODE = UUID.fromString("7d87e5dc-cf71-421f-9e7c-853155898d67");
    private static final String PROOF = "a".repeat(64);
    private static final String PROOF_HASH = "b".repeat(64);
    private static final String IDENTITY_JSON = """
            {
                "provider": "google",
                "providerUserId": "provider-user-123",
                "displayName": "Test User",
                "email": "test@example.com",
                "proofHash": "%s"
            }
            """.formatted(PROOF_HASH);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private TokenService sessions;

    private static final UserProfile USER = new UserProfile(
            17L, "google", "provider-user-123", "Test User", "test@example.com");
    private static final AuthTokens TOKENS = new AuthTokens(USER, "a".repeat(43), "b".repeat(43),
            Duration.ofMinutes(15), Duration.ofDays(30));

    @Test
    void createsHandoffFromAuthorizedIdentity() throws Exception {
        when(auth.isHandoffAuthorized("Bearer handoff-secret")).thenReturn(true);
        when(auth.startLogin(any(OAuthIdentity.class))).thenReturn(CODE);

        mvc.perform(post("/internal/auth/login-handoffs")
                        .header("Authorization", "Bearer handoff-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IDENTITY_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(CODE.toString()));

        verify(auth).startLogin(new OAuthIdentity(
                "google", "provider-user-123", "Test User", "test@example.com", PROOF_HASH));
    }

    @Test
    void rejectsUnauthorizedHandoffCreation() throws Exception {
        mvc.perform(post("/internal/auth/login-handoffs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IDENTITY_JSON))
                .andExpect(status().isUnauthorized());

        verify(auth, never()).startLogin(any(OAuthIdentity.class));
    }

    @Test
    void validatesIncomingIdentity() throws Exception {
        mvc.perform(post("/internal/auth/login-handoffs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IDENTITY_JSON.replace("\"google\"", "\"unsupported\"")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(auth);
    }

    @Test
    void completesBrowserPostAndIssuesAuthCookies() throws Exception {
        when(sessions.completeLogin(CODE, PROOF)).thenReturn(Optional.of(TOKENS));

        var result = mvc.perform(post("/api/auth/login-handoffs/complete")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("code", CODE.toString())
                        .cookie(new Cookie("hypertube_login_proof", PROOF)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.id").value(17))
                .andExpect(jsonPath("$.provider").value("google"))
                .andExpect(jsonPath("$.providerUserId").value("provider-user-123"))
                .andExpect(jsonPath("$.displayName").value("Test User"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(cookie().value(AuthCookies.ACCESS, TOKENS.accessToken()))
                .andExpect(cookie().httpOnly(AuthCookies.ACCESS, true))
                .andExpect(cookie().secure(AuthCookies.ACCESS, true))
                .andExpect(cookie().path(AuthCookies.ACCESS, "/"))
                .andExpect(cookie().maxAge(AuthCookies.ACCESS, 900))
                .andExpect(cookie().value(AuthCookies.REFRESH, TOKENS.refreshToken()))
                .andExpect(cookie().httpOnly(AuthCookies.REFRESH, true))
                .andExpect(cookie().secure(AuthCookies.REFRESH, true))
                .andExpect(cookie().path(AuthCookies.REFRESH, "/api/auth"))
                .andExpect(cookie().maxAge("hypertube_login_proof", 0))
                .andReturn();

        assertNull(result.getRequest().getSession(false));
        assertEquals("Lax", result.getResponse().getCookie(AuthCookies.ACCESS).getAttribute("SameSite"));
        assertTrue(result.getResponse().getHeaders("Set-Cookie").stream().noneMatch(value -> value.contains("JSESSIONID")));
    }

    @Test
    void rejectsInvalidOrConsumedHandoff() throws Exception {
        when(sessions.completeLogin(CODE, PROOF)).thenReturn(Optional.empty());

        mvc.perform(post("/api/auth/login-handoffs/complete")
                        .param("code", CODE.toString())
                        .cookie(new Cookie("hypertube_login_proof", PROOF)))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist(AuthCookies.ACCESS))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void rejectsGetForHandoffCompletion() throws Exception {
        mvc.perform(get("/api/auth/login-handoffs/complete").param("code", CODE.toString()))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(auth);
    }

    @Test
    void refreshesUsingTheRefreshCookieAndCsrfToken() throws Exception {
        when(sessions.refresh("old-refresh")).thenReturn(Optional.of(TOKENS));
        mvc.perform(postWithCsrf("/api/auth/refresh")
                        .cookie(new Cookie(AuthCookies.REFRESH, "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(cookie().value(AuthCookies.ACCESS, TOKENS.accessToken()))
                .andExpect(cookie().value(AuthCookies.REFRESH, TOKENS.refreshToken()))
                .andExpect(jsonPath("$.id").value(17));
    }

    @Test
    void clearsCookiesWhenRefreshIsInvalid() throws Exception {
        mvc.perform(postWithCsrf("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge(AuthCookies.ACCESS, 0))
                .andExpect(cookie().maxAge(AuthCookies.REFRESH, 0));
    }

    @Test
    void logsOutAndClearsBothCookies() throws Exception {
        mvc.perform(postWithCsrf("/api/auth/logout")
                        .cookie(new Cookie(AuthCookies.ACCESS, "access"), new Cookie(AuthCookies.REFRESH, "refresh")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(cookie().maxAge(AuthCookies.ACCESS, 0))
                .andExpect(cookie().maxAge(AuthCookies.REFRESH, 0));
        verify(sessions).logout("access", "refresh");
    }

    @Test
    void rejectsRefreshAndLogoutWithoutCsrfProtection() throws Exception {
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());
        verifyNoInteractions(sessions);
    }

    @Test
    void exposesCsrfTokenWithoutCreatingServletSession() throws Exception {
        var result = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void authenticatesAccessCookieForCurrentUser() throws Exception {
        when(sessions.authenticate(TOKENS.accessToken())).thenReturn(Optional.of(USER));
        mvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookies.ACCESS, TOKENS.accessToken())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(17));
    }

    @Test
    void rejectsMissingOrInvalidAccessCookies() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookies.ACCESS, "invalid")))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder postWithCsrf(String path) throws Exception {
        var result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        return post(path).cookie(result.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", token);
    }
}
