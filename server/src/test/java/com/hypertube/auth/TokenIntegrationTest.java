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
        "hypertube.auth.tokens.sliding-ttl=7d",
        "hypertube.auth.tokens.absolute-ttl=30d",
        "hypertube.auth.tokens.refresh-grace=5s",
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
    void issuesBearerJwtAndSecureOpaqueRefreshCookie() throws Exception {
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
        mvc.perform(get("/api/auth/me").cookie(new Cookie("hypertube_access", device.access())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRenewsSlidingExpiryWithoutChangingAbsoluteExpiry() throws Exception {
        Credentials original = login();
        clock.advance(Duration.ofDays(6));
        Credentials renewed = rotate(original);
        assertThat(instant("sliding_expires_at", original.id())).isEqualTo(START.plus(Duration.ofDays(13)));
        assertThat(instant("absolute_expires_at", original.id())).isEqualTo(START.plus(Duration.ofDays(30)));
        assertThat(jdbc.queryForObject("SELECT previous_token_hash FROM user_sessions", String.class))
                .isEqualTo(TokenValues.hash(original.refresh().getValue()));
        assertThat(renewed.refresh().getValue()).isNotEqualTo(original.refresh().getValue());
        me(renewed).andExpect(status().isOk());
    }

    @Test
    void absoluteDeadlineCapsSlidingRenewalAndRejectsExactlyAtDeadline() throws Exception {
        Credentials device = login();
        for (int i = 0; i < 4; i++) {
            clock.advance(Duration.ofDays(6));
            device = rotate(device);
        }
        clock.advance(Duration.ofDays(5));
        device = rotate(device);
        assertThat(instant("sliding_expires_at", device.id())).isEqualTo(START.plus(Duration.ofDays(30)));
        assertThat(instant("absolute_expires_at", device.id())).isEqualTo(START.plus(Duration.ofDays(30)));
        clock.set(START.plus(Duration.ofDays(30)).minusSeconds(60));
        device = rotate(device);
        assertThat(jwt.verify(device.access()).orElseThrow().expiresAt()).isEqualTo(START.plus(Duration.ofDays(30)));
        clock.set(START.plus(Duration.ofDays(30)));
        refresh(device.refresh()).andExpect(status().isUnauthorized()).andExpect(cookie().maxAge(AuthCookies.REFRESH, 0));
        assertThat(revoked(device.id())).isTrue();
    }

    @Test
    void slidingExpiryIsStrictAndRevocationCommitsDespiteUnauthorizedResponse() throws Exception {
        Credentials device = login();
        clock.advance(Duration.ofDays(7));
        refresh(device.refresh()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("session_expired"));
        assertThat(revoked(device.id())).isTrue();
    }

    @Test
    void previousTokenWithinGraceDoesNotRotateOrExtendWindow() throws Exception {
        Credentials original = login();
        Credentials winner = rotate(original);
        Instant deadline = instant("previous_token_expires_at", original.id());
        clock.advance(Duration.ofSeconds(4));
        var duplicate = refresh(original.refresh()).andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        Credentials duplicateAccess = from(duplicate, winner.refresh());
        me(duplicateAccess).andExpect(status().isOk());
        assertThat(instant("previous_token_expires_at", original.id())).isEqualTo(deadline);
        assertThat(jdbc.queryForObject("SELECT refresh_token_hash FROM user_sessions", String.class))
                .isEqualTo(TokenValues.hash(winner.refresh().getValue()));
        clock.advance(Duration.ofSeconds(1));
        refresh(original.refresh()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("refresh_reuse"));
        assertThat(revoked(original.id())).isTrue();
        me(winner).andExpect(status().isUnauthorized());
        me(duplicateAccess).andExpect(status().isUnauthorized());
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
    void overlappingRotationsPreserveEachConsumedTokensOriginalGraceDeadline() throws Exception {
        Credentials original = login();
        Credentials first = rotate(original);
        clock.advance(Duration.ofSeconds(1));
        Credentials second = rotate(first);
        clock.advance(Duration.ofSeconds(2));
        refresh(original.refresh()).andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie"));
        clock.set(START.plusSeconds(5));
        refresh(original.refresh()).andExpect(status().isUnauthorized());
        me(second).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRefreshesReturnAccessButOnlyOneRotatesTheCookie() throws Exception {
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
            assertThat(results).allMatch(result -> result.getResponse().getStatus() == 200);
            var winners = results.stream().filter(result -> result.getResponse().getCookie(AuthCookies.REFRESH) != null).toList();
            assertThat(winners).hasSize(1);
            Credentials winner = from(winners.getFirst(), null);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token_history", Integer.class)).isEqualTo(1);
            assertThat(revoked(original.id())).isFalse();
            for (var result : results) {
                me(from(result, winner.refresh())).andExpect(status().isOk());
            }
            rotate(winner);
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
        mvc.perform(withCsrf("/api/auth/logout", first.refresh()).header("Authorization", bearer(first)))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge(AuthCookies.REFRESH, 0));
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
        mvc.perform(withCsrf("/api/auth/logout", second.refresh()).header("Authorization", bearer(first)))
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
                .andExpect(cookie().maxAge(AuthCookies.REFRESH, 0));
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
        String access = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        var claims = jwt.verify(access).orElseThrow();
        Cookie refresh = result.getResponse().getCookie(AuthCookies.REFRESH);
        return new Credentials(access, refresh == null ? fallback : refresh, claims.userId(), claims.sessionId());
    }
    private Credentials rotate(Credentials old) throws Exception { return from(refresh(old.refresh()).andExpect(status().isOk()).andReturn(), old.refresh()); }
    private ResultActions refresh(Cookie cookie) throws Exception { return mvc.perform(withCsrf("/api/auth/refresh", cookie)); }
    private ResultActions me(Credentials device) throws Exception { return mvc.perform(get("/api/auth/me").header("Authorization", bearer(device))); }
    private String bearer(Credentials device) { return "Bearer " + device.access(); }
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
