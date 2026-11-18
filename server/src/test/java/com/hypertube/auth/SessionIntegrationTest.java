package com.hypertube.auth;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.hypertube.auth.security.SessionCookies;
import com.hypertube.auth.security.TokenValues;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DATABASE_URL}",
        "spring.datasource.username=${TEST_DATABASE_USER:postgres}",
        "spring.datasource.password=${TEST_DATABASE_PASSWORD:}",
        "hypertube.auth.handoff-secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "hypertube.auth.session.access-ttl=15m",
        "hypertube.auth.session.refresh-ttl=30d",
        "hypertube.auth.session.secure-cookies=false"
})
@AutoConfigureMockMvc
class SessionIntegrationTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String PROOF = "a".repeat(64);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearTestDatabase() {
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM oauth_login_handoffs");
    }

    @Test
    void createsHashedSessionAndAuthenticatesAccessCookie() throws Exception {
        BrowserSession session = login();
        mvc.perform(get("/api/auth/me").cookie(session.access()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.providerUserId").value("session-test"));
        assertThat(jdbc.queryForObject("SELECT access_token_hash FROM auth_sessions", String.class))
                .isEqualTo(TokenValues.hash(session.access().getValue()));
        assertThat(jdbc.queryForObject("SELECT token_hash FROM auth_refresh_tokens", String.class))
                .isEqualTo(TokenValues.hash(session.refresh().getValue()));
        assertThat(session.access().isHttpOnly()).isTrue();
        assertThat(session.refresh().isHttpOnly()).isTrue();
        assertThat(session.access().getValue()).isNotEqualTo(session.refresh().getValue());
    }

    @Test
    void rotatesBothTokensAndInvalidatesPreviousAccess() throws Exception {
        BrowserSession original = login();
        BrowserSession rotated = tokens(postWithCsrf("/api/auth/refresh", original.refresh())
                .andExpect(status().isOk()).andReturn());
        assertThat(rotated.access().getValue()).isNotEqualTo(original.access().getValue());
        assertThat(rotated.refresh().getValue()).isNotEqualTo(original.refresh().getValue());
        mvc.perform(get("/api/auth/me").cookie(original.access())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(rotated.access())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_refresh_tokens WHERE used_at IS NOT NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void replayedRefreshRevokesTheWholeSession() throws Exception {
        BrowserSession original = login();
        BrowserSession rotated = tokens(postWithCsrf("/api/auth/refresh", original.refresh())
                .andExpect(status().isOk()).andReturn());
        postWithCsrf("/api/auth/refresh", original.refresh()).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(rotated.access())).andExpect(status().isUnauthorized());
        postWithCsrf("/api/auth/refresh", rotated.refresh()).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM auth_sessions", Boolean.class)).isTrue();
    }

    @Test
    void expiredAccessCanStillRefresh() throws Exception {
        BrowserSession session = login();
        jdbc.update("UPDATE auth_sessions SET access_expires_at = now() - interval '1 second'");
        mvc.perform(get("/api/auth/me").cookie(session.access())).andExpect(status().isUnauthorized());
        BrowserSession rotated = tokens(postWithCsrf("/api/auth/refresh", session.refresh())
                .andExpect(status().isOk()).andReturn());
        mvc.perform(get("/api/auth/me").cookie(rotated.access())).andExpect(status().isOk());
    }

    @Test
    void expiredSessionCannotRefreshAndIsCleanedUpOnLogin() throws Exception {
        BrowserSession session = login();
        jdbc.update("UPDATE auth_sessions SET access_expires_at = now() - interval '2 seconds', expires_at = now() - interval '1 second'");
        postWithCsrf("/api/auth/refresh", session.refresh()).andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge(SessionCookies.ACCESS, 0))
                .andExpect(cookie().maxAge(SessionCookies.REFRESH, 0));
        mvc.perform(get("/api/auth/me").cookie(session.access())).andExpect(status().isUnauthorized());
        login();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_refresh_tokens", Integer.class)).isEqualTo(1);
    }

    @Test
    void refreshingDoesNotExtendAbsoluteSessionLifetime() throws Exception {
        BrowserSession session = login();
        var expiry = jdbc.queryForObject("SELECT expires_at FROM auth_sessions", java.sql.Timestamp.class);
        jdbc.update("UPDATE auth_sessions SET expires_at = now() + interval '10 seconds', access_expires_at = now()");
        var shortenedExpiry = jdbc.queryForObject("SELECT expires_at FROM auth_sessions", java.sql.Timestamp.class);
        BrowserSession rotated = tokens(postWithCsrf("/api/auth/refresh", session.refresh())
                .andExpect(status().isOk()).andReturn());
        assertThat(rotated.access().getMaxAge()).isBetween(1, 10);
        assertThat(rotated.refresh().getMaxAge()).isBetween(1, 10);
        assertThat(jdbc.queryForObject("SELECT expires_at FROM auth_sessions", java.sql.Timestamp.class))
                .isEqualTo(shortenedExpiry).isBefore(expiry);
        assertThat(jdbc.queryForObject("SELECT access_expires_at <= expires_at FROM auth_sessions", Boolean.class)).isTrue();
    }

    @Test
    void logoutRevokesTokensAndClearsCookiesIdempotently() throws Exception {
        BrowserSession session = login();
        postWithCsrf("/api/auth/logout", session.access(), session.refresh()).andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(SessionCookies.ACCESS, 0))
                .andExpect(cookie().maxAge(SessionCookies.REFRESH, 0));
        mvc.perform(get("/api/auth/me").cookie(session.access())).andExpect(status().isUnauthorized());
        postWithCsrf("/api/auth/refresh", session.refresh()).andExpect(status().isUnauthorized());
        postWithCsrf("/api/auth/logout", session.access(), session.refresh()).andExpect(status().isNoContent());
        postWithCsrf("/api/auth/logout").andExpect(status().isNoContent());
    }

    @Test
    void logoutWorksWithOnlyARefreshCookieAndKeepsOtherSessionsActive() throws Exception {
        BrowserSession first = login();
        BrowserSession second = login();
        postWithCsrf("/api/auth/logout", first.refresh()).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(first.access())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(second.access())).andExpect(status().isOk());
        postWithCsrf("/api/auth/refresh", second.refresh()).andExpect(status().isOk());
    }

    @Test
    void logoutWithOnlyAnAccessCookieAlsoRevokesRefresh() throws Exception {
        BrowserSession session = login();
        postWithCsrf("/api/auth/logout", session.access()).andExpect(status().isNoContent());
        postWithCsrf("/api/auth/refresh", session.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void missingAndMalformedCredentialsCannotCreateSessions() throws Exception {
        mvc.perform(post("/api/auth/login-handoffs/complete").param("code", UUID.randomUUID().toString())
                        .cookie(new Cookie("hypertube_login_proof", PROOF)))
                .andExpect(status().isUnauthorized()).andExpect(cookie().doesNotExist(SessionCookies.ACCESS));
        postWithCsrf("/api/auth/refresh").andExpect(status().isUnauthorized());
        postWithCsrf("/api/auth/refresh", new Cookie(SessionCookies.REFRESH, "invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions", Integer.class)).isZero();
    }

    @Test
    void sessionCreationFailureRollsBackHandoffConsumptionAndUserUpsert() throws Exception {
        String code = createHandoff();
        jdbc.execute("ALTER TABLE auth_sessions ADD CONSTRAINT reject_test_sessions CHECK (false) NOT VALID");
        try {
            assertThrows(ServletException.class, () -> complete(code));
        } finally {
            jdbc.execute("ALTER TABLE auth_sessions DROP CONSTRAINT reject_test_sessions");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM oauth_login_handoffs", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
        complete(code).andExpect(status().isOk());
        complete(code).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions", Integer.class)).isEqualTo(1);
    }

    @Test
    void refreshAndLogoutRequireARealCsrfCookieAndHeader() throws Exception {
        BrowserSession session = login();
        mvc.perform(post("/api/auth/refresh").cookie(session.refresh())).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(session.access(), session.refresh())).andExpect(status().isForbidden());
        var csrf = mvc.perform(get("/api/auth/csrf")).andReturn();
        mvc.perform(post("/api/auth/refresh").cookie(session.refresh(), csrf.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", "invalid"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").cookie(session.access())).andExpect(status().isOk());
    }

    @Test
    void concurrentRefreshesCannotBothSucceed() throws Exception {
        BrowserSession session = login();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<MvcResult> refresh = () -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return postWithCsrf("/api/auth/refresh", session.refresh()).andReturn();
            };
            var first = executor.submit(refresh);
            var second = executor.submit(refresh);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().map(result -> result.getResponse().getStatus()).sorted().toList())
                    .containsExactly(200, 401);
            var winner = results.stream().filter(result -> result.getResponse().getStatus() == 200).findFirst().orElseThrow();
            mvc.perform(get("/api/auth/me").cookie(tokens(winner).access())).andExpect(status().isUnauthorized());
        }
    }

    private String createHandoff() throws Exception {
        return mvc.perform(post("/internal/auth/login-handoffs")
                        .header("Authorization", "Bearer " + SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"google","providerUserId":"session-test","displayName":"Test User",
                                 "email":"test@example.com","proofHash":"%s"}
                                """.formatted(TokenValues.hash(PROOF))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private ResultActions complete(String code) throws Exception {
        return mvc.perform(post("/api/auth/login-handoffs/complete").param("code", code)
                .cookie(new Cookie("hypertube_login_proof", PROOF)));
    }

    private BrowserSession login() throws Exception {
        return tokens(complete(createHandoff()).andExpect(status().isOk()).andReturn());
    }

    private BrowserSession tokens(MvcResult result) {
        return new BrowserSession(result.getResponse().getCookie(SessionCookies.ACCESS),
                result.getResponse().getCookie(SessionCookies.REFRESH));
    }

    private ResultActions postWithCsrf(String path, Cookie... cookies) throws Exception {
        var csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        var request = post(path).cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", token);
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        return mvc.perform(request);
    }

    private record BrowserSession(Cookie access, Cookie refresh) {}
}
