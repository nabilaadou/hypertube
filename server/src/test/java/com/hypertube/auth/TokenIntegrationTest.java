package com.hypertube.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import com.hypertube.auth.security.AuthCookies;
import com.hypertube.auth.security.JwtService;
import com.hypertube.auth.security.TokenValues;
import com.hypertube.auth.service.SessionCleanupService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DATABASE_URL}",
        "spring.datasource.username=${TEST_DATABASE_USER:postgres}",
        "spring.datasource.password=${TEST_DATABASE_PASSWORD:}",
        "hypertube.auth.handoff-secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "hypertube.auth.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==",
        "hypertube.auth.tokens.access-ttl=10m",
        "hypertube.auth.tokens.refresh-ttl=30d",
        "hypertube.auth.tokens.secure-cookies=true",
        "hypertube.auth.cleanup-interval=PT24H"
})
@AutoConfigureMockMvc
@Import(TokenIntegrationTest.ClockConfiguration.class)
class TokenIntegrationTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String PROOF = "a".repeat(64);
    private static final Instant START = Instant.parse("2026-11-18T12:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private MutableClock clock;
    @Autowired private SessionCleanupService cleanup;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM oauth_login_handoffs");
        jdbc.update("DELETE FROM revoked_sessions");
        clock.set(START);
    }

    @Test
    void issuesHttpOnlyAccessAndOpaqueRefreshCookies() throws Exception {
        Credentials device = login();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns WHERE table_name = 'user_sessions'
                AND column_name IN ('device_info', 'ip_address', 'created_at', 'last_used_at')
                """, Integer.class)).isZero();
        var claims = jwt.verify(device.access()).orElseThrow();
        assertThat(claims.userId()).isEqualTo(device.userId());
        assertThat(claims.sessionId()).isEqualTo(device.id());
        String payload = new String(java.util.Base64.getUrlDecoder().decode(device.access().split("\\.")[1]),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(payload).doesNotContain("\"authorities\"", "\"roles\"");
        assertThat(claims.expiresAt()).isEqualTo(START.plusSeconds(600));
        assertThat(device.refresh().getValue()).hasSize(43);
        assertThat(jdbc.queryForObject("SELECT refresh_token_hash FROM user_sessions", String.class))
                .isEqualTo(TokenValues.hash(device.refresh().getValue())).isNotEqualTo(device.refresh().getValue());
        assertThat(device.refresh().isHttpOnly()).isTrue();
        assertThat(device.refresh().getSecure()).isTrue();
        assertThat(device.refresh().getPath()).isEqualTo("/api/auth");
        assertThat(device.refresh().getAttribute("SameSite")).isEqualTo("Strict");
        me(device).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + device.access()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void handoffRequiresTheBrowserProofAndRemainsUsableAfterWrongProof() throws Exception {
        String code = createHandoff("session-test");
        mvc.perform(post("/api/auth/login-handoffs/complete").param("code", code))
                .andExpect(status().isUnauthorized()).andExpect(cookie().doesNotExist(AuthCookies.ACCESS));
        mvc.perform(post("/api/auth/login-handoffs/complete").param("code", code)
                        .cookie(new Cookie("hypertube_login_proof", "b".repeat(64))))
                .andExpect(status().isUnauthorized()).andExpect(cookie().doesNotExist(AuthCookies.ACCESS));
        from(mvc.perform(complete(code)).andExpect(status().isOk()).andReturn(), null);
        mvc.perform(complete(code)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTokensButNeverMovesTheFixedExpiry() throws Exception {
        Credentials current = login();
        Instant deadline = START.plus(Duration.ofDays(30));
        for (int i = 0; i < 3; i++) {
            clock.advance(Duration.ofDays(8));
            Credentials renewed = rotate(current);
            assertThat(renewed.refresh().getValue()).isNotEqualTo(current.refresh().getValue());
            assertThat(instant("expires_at", renewed.id())).isEqualTo(deadline);
            assertThat(renewed.refresh().getMaxAge()).isEqualTo((int) Duration.between(clock.instant(), deadline).toSeconds());
            me(renewed).andExpect(status().isOk());
            current = renewed;
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns WHERE table_name = 'user_sessions'
                AND column_name IN ('sliding_expires_at', 'absolute_expires_at', 'previous_token_hash', 'previous_token_expires_at')
                """, Integer.class)).isZero();
    }

    @Test
    void accessExpiryIsCappedAndRefreshFailsAtTheFixedDeadline() throws Exception {
        Credentials original = login();
        Instant deadline = START.plus(Duration.ofDays(30));
        clock.set(deadline.minusSeconds(60));
        Credentials renewed = rotate(original);
        assertThat(jwt.verify(renewed.access()).orElseThrow().expiresAt()).isEqualTo(deadline);
        assertThat(renewed.refresh().getMaxAge()).isEqualTo(60);
        clock.set(deadline);
        refresh(renewed.refresh()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("session_expired"))
                .andExpect(cookie().maxAge(AuthCookies.REFRESH, 0))
                .andExpect(cookie().maxAge(AuthCookies.ACCESS, 0));
        assertThat(revoked(original.id())).isTrue();
        me(renewed).andExpect(status().isUnauthorized());
    }

    @Test
    void reuseOfAConsumedRefreshTokenRevokesTheSession() throws Exception {
        Credentials original = login();
        Credentials renewed = rotate(original);
        refresh(original.refresh()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("refresh_reuse"));
        assertThat(revoked(original.id())).isTrue();
        me(renewed).andExpect(status().isUnauthorized());
        refresh(renewed.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void olderTokenHistoryDetectsTheftWithoutRevokingAnotherSession() throws Exception {
        Credentials stolen = login();
        Credentials other = login();
        Credentials current = rotate(rotate(stolen));
        clock.advance(Duration.ofSeconds(6));
        refresh(stolen.refresh()).andExpect(status().isUnauthorized());
        me(current).andExpect(status().isUnauthorized());
        refresh(current.refresh()).andExpect(status().isUnauthorized());
        me(other).andExpect(status().isOk());
    }

    @Test
    void concurrentRefreshesCannotBothConsumeTheSameToken() throws Exception {
        Credentials original = login();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<MvcResult> request = () -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return refresh(original.refresh()).andReturn();
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().map(result -> result.getResponse().getStatus()).sorted().toList())
                    .containsExactly(200, 401);
            var winners = results.stream().filter(result -> result.getResponse().getStatus() == 200).toList();
            assertThat(winners).hasSize(1);
            Credentials winner = from(winners.getFirst(), null);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token_history", Integer.class)).isEqualTo(1);
            assertThat(revoked(original.id())).isTrue();
            me(winner).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void expiredAccessDoesNotPreventRefresh() throws Exception {
        Credentials device = login();
        clock.advance(Duration.ofMinutes(10));
        me(device).andExpect(status().isUnauthorized());
        me(rotate(device)).andExpect(status().isOk());
    }

    @Test
    void currentLogoutRevokesOnlyThatSessionAndIsIdempotent() throws Exception {
        Credentials first = login();
        Credentials second = login();
        mvc.perform(withCsrf("/api/auth/logout", first.refresh()).cookie(new Cookie(AuthCookies.ACCESS, first.access())))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge(AuthCookies.REFRESH, 0))
                .andExpect(cookie().maxAge(AuthCookies.ACCESS, 0));
        me(first).andExpect(status().isUnauthorized());
        refresh(first.refresh()).andExpect(status().isUnauthorized());
        me(second).andExpect(status().isOk());
        rotate(second);
        mvc.perform(withCsrf("/api/auth/logout", first.refresh())).andExpect(status().isNoContent());
        mvc.perform(withCsrf("/api/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    void authenticatedSessionTakesPrecedenceOverMismatchedRefreshCookie() throws Exception {
        Credentials first = login();
        Credentials second = login();
        mvc.perform(withCsrf("/api/auth/logout", second.refresh()).cookie(new Cookie(AuthCookies.ACCESS, first.access())))
                .andExpect(status().isNoContent());
        assertThat(revoked(first.id())).isTrue();
        assertThat(revoked(second.id())).isFalse();
        me(second).andExpect(status().isOk());
    }

    @Test
    void cookieOnlyLogoutWorksAfterAccessExpiry() throws Exception {
        Credentials device = login();
        clock.advance(Duration.ofMinutes(11));
        mvc.perform(withCsrf("/api/auth/logout", device.refresh())).andExpect(status().isNoContent());
        refresh(device.refresh()).andExpect(status().isUnauthorized());
        assertThat(revoked(device.id())).isTrue();
    }

    @Test
    void cleanupDeletesRevokedRowsAndHistoryWithoutResurrectingAccessJwt() throws Exception {
        Credentials first = rotate(login());
        Credentials second = login();
        mvc.perform(withCsrf("/api/auth/logout", first.refresh())).andExpect(status().isNoContent());
        cleanup.cleanup();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_sessions", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token_history", Integer.class)).isZero();
        me(first).andExpect(status().isUnauthorized());
        me(second).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM revoked_sessions", Integer.class)).isEqualTo(1);
        clock.advance(Duration.ofDays(31));
        cleanup.cleanup();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_sessions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM revoked_sessions", Integer.class)).isZero();
    }

    @Test
    void failedSessionInsertRollsBackHandoffAndUserUpsert() throws Exception {
        String code = createHandoff("session-test");
        jdbc.execute("ALTER TABLE user_sessions ADD CONSTRAINT reject_test_session CHECK (false) NOT VALID");
        try { assertThrows(ServletException.class, () -> mvc.perform(complete(code))); }
        finally { jdbc.execute("ALTER TABLE user_sessions DROP CONSTRAINT reject_test_session"); }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM oauth_login_handoffs", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
        mvc.perform(complete(code)).andExpect(status().isOk());
        mvc.perform(complete(code)).andExpect(status().isUnauthorized());
    }

    @Test
    void cookieEndpointsRejectFormCsrfTokensWithoutTheRequiredHeader() throws Exception {
        Credentials device = login();
        var response = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse();
        String token = JsonPath.read(response.getContentAsString(), "$.token");
        String parameter = JsonPath.read(response.getContentAsString(), "$.parameterName");
        for (String path : List.of("/api/auth/refresh", "/api/auth/logout")) {
            mvc.perform(post(path).cookie(device.refresh(), response.getCookie("XSRF-TOKEN"))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED).param(parameter, token))
                    .andExpect(status().isForbidden());
        }
        me(device).andExpect(status().isOk());
    }

    @Test
    void csrfAndAuthenticationFailuresAreStructuredAndDoNotCreateSessions() throws Exception {
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        mvc.perform(withCsrf("/api/auth/refresh")).andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge(AuthCookies.REFRESH, 0))
                .andExpect(cookie().maxAge(AuthCookies.ACCESS, 0));
        mvc.perform(withCsrf("/api/auth/refresh", new Cookie(AuthCookies.REFRESH, "invalid"))).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_sessions", Integer.class)).isZero();
    }

    private Credentials login() throws Exception {
        return from(mvc.perform(complete(createHandoff("session-test"))).andExpect(status().isOk()).andReturn(), null);
    }
    private String createHandoff(String account) throws Exception {
        return mvc.perform(post("/internal/auth/login-handoffs").header("Authorization", "Bearer " + SECRET)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"provider":"google","providerUserId":"%s","displayName":"Test User","proofHash":"%s"}
                        """.formatted(account, TokenValues.hash(PROOF))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    private MockHttpServletRequestBuilder complete(String code) {
        return post("/api/auth/login-handoffs/complete").param("code", code).cookie(new Cookie("hypertube_login_proof", PROOF));
    }
    private Credentials from(MvcResult result, Cookie fallback) throws Exception {
        assertThat(JsonPath.<java.util.Map<String, Object>>read(result.getResponse().getContentAsString(), "$")).doesNotContainKeys("accessToken", "refreshToken", "tokenType");
        Cookie accessCookie = result.getResponse().getCookie(AuthCookies.ACCESS);
        assertThat(accessCookie).isNotNull();
        assertThat(accessCookie.isHttpOnly()).isTrue();
        assertThat(accessCookie.getSecure()).isTrue();
        assertThat(accessCookie.getPath()).isEqualTo("/");
        assertThat(accessCookie.getAttribute("SameSite")).isEqualTo("Strict");
        String access = accessCookie.getValue();
        var claims = jwt.verify(access).orElseThrow();
        Cookie refresh = result.getResponse().getCookie(AuthCookies.REFRESH);
        return new Credentials(access, refresh == null ? fallback : refresh, claims.userId(), claims.sessionId());
    }
    private Credentials rotate(Credentials old) throws Exception { return from(refresh(old.refresh()).andExpect(status().isOk()).andReturn(), old.refresh()); }
    private ResultActions refresh(Cookie cookie) throws Exception { return mvc.perform(withCsrf("/api/auth/refresh", cookie)); }
    private ResultActions me(Credentials device) throws Exception { return mvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookies.ACCESS, device.access()))); }
    private MockHttpServletRequestBuilder withCsrf(String path, Cookie... cookies) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        String token = JsonPath.read(response.getContentAsString(), "$.token");
        var request = post(path).cookie(response.getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", token);
        if (cookies.length > 0) { request.cookie(cookies); }
        return request;
    }
    private Instant instant(String column, UUID id) {
        return jdbc.queryForObject("SELECT " + column + " FROM user_sessions WHERE id = ?", java.sql.Timestamp.class, id).toInstant();
    }
    private boolean revoked(UUID id) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT revoked FROM user_sessions WHERE id = ?", Boolean.class, id)); }
    private record Credentials(String access, Cookie refresh, long userId, UUID id) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(START);
        void set(Instant instant) { now.set(instant); }
        void advance(Duration duration) { now.updateAndGet(instant -> instant.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return now.get(); }
    }
}
