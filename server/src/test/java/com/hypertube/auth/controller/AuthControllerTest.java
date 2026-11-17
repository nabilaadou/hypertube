package com.hypertube.auth.controller;

import java.util.Optional;
import java.util.UUID;

import com.hypertube.auth.schema.OAuthIdentity;
import com.hypertube.auth.schema.UserProfile;
import com.hypertube.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
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
    void completesBrowserPostAndReturnsProfileWithoutCreatingSession() throws Exception {
        when(auth.finishLogin(CODE, PROOF)).thenReturn(Optional.of(
                new UserProfile(17L, "google", "provider-user-123", "Test User", "test@example.com")));

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
                .andReturn();

        assertNull(result.getRequest().getSession(false));
        assertNull(result.getResponse().getHeader("Set-Cookie"));
    }

    @Test
    void rejectsInvalidOrConsumedHandoff() throws Exception {
        when(auth.finishLogin(CODE, PROOF)).thenReturn(Optional.empty());

        mvc.perform(post("/api/auth/login-handoffs/complete")
                        .param("code", CODE.toString())
                        .cookie(new Cookie("hypertube_login_proof", PROOF)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void rejectsGetForHandoffCompletion() throws Exception {
        mvc.perform(get("/api/auth/login-handoffs/complete").param("code", CODE.toString()))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(auth);
    }
}
