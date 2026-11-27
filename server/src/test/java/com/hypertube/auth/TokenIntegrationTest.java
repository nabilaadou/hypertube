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
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DATABASE_URL}",
        "spring.datasource.username=${TEST_DATABASE_USER:postgres}",
        "spring.datasource.password=${TEST_DATABASE_PASSWORD:}",
        "hypertube.auth.handoff-secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "hypertube.auth.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==",
        "hypertube.auth.access-ttl=10m",
        "hypertube.auth.refresh-ttl=30d",
        "hypertube.auth.secure-cookies=true",
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
    @Autowired private ScheduledTaskHolder scheduledTasks;
    @MockitoBean private JavaMailSender mail;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM oauth_login_handoffs");
        jdbc.update("DELETE FROM revoked_sessions");
        clock.set(START);
    }

    @Test
    void concurrentResetConfirmationsConsumeTheLinkOnlyOnce() throws Exception {
        localRegistration("reset_race", "reset-race@example.com");
        requestReset("reset-race@example.com").andExpect(status().isAccepted());
        String token = sentResetToken();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> request = () -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return confirmReset(token).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)).stream().sorted().toList())
                    .containsExactly(200, 401);
        }
    }

    @Test
    void passwordResetRevokesOldSessionsAndCreatesANewOne() throws Exception {
        var original = localRegistration("reset_user", "reset@example.com");
        var other = from(mvc.perform(withCsrf("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reset_user\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isOk()).andReturn());
        requestReset("reset@example.com").andExpect(status().isAccepted());
        String token = sentResetToken();
        assertThat(jdbc.queryForObject("SELECT token_hash FROM password_reset_tokens", String.class))
                .isEqualTo(jwt.hash(token)).isNotEqualTo(token);
        var replacement = from(confirmReset(token).andExpect(status().isOk()).andReturn());
        assertThat(replacement.userId()).isEqualTo(original.userId());
        me(original).andExpect(status().isUnauthorized());
        me(other).andExpect(status().isUnauthorized());
        refresh(original.refresh()).andExpect(status().isUnauthorized());
        me(replacement).andExpect(status().isOk());
        confirmReset(token).andExpect(status().isUnauthorized());
        mvc.perform(withCsrf("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reset_user\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(withCsrf("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reset_user\",\"password\":\"new-strong-password\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void resetRequestsAreGenericRateLimitedAndReplacePreviousLinks() throws Exception {
        localRegistration("reset_second", "reset-second@example.com");
        requestReset("missing@example.com").andExpect(status().isAccepted());
        jdbc.update("INSERT INTO users (provider, provider_user_id, email) VALUES ('google', 'reset-provider', 'oauth-reset@example.com')");
        requestReset("oauth-reset@example.com").andExpect(status().isAccepted());
        verifyNoInteractions(mail);
        requestReset("reset-second@example.com").andExpect(status().isAccepted());
        String first = sentResetToken();
        requestReset("RESET-SECOND@example.com").andExpect(status().isAccepted());
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
        clock.advance(Duration.ofMinutes(1));
        requestReset("reset-second@example.com").andExpect(status().isAccepted());
        var messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(2)).send(messages.capture());
        String second = tokenFrom(messages.getAllValues().getLast());
        assertThat(second).isNotEqualTo(first);
        confirmReset(first).andExpect(status().isUnauthorized());
        clock.advance(Duration.ofMinutes(30));
        confirmReset(second).andExpect(status().isUnauthorized());
        cleanup.cleanup();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_tokens", Integer.class)).isZero();
    }

    @Test
    void mailFailureKeepsThePreviousResetLinkAndGenericResponse() throws Exception {
        localRegistration("reset_third", "reset-third@example.com");
        requestReset("reset-third@example.com").andExpect(status().isAccepted());
        String first = sentResetToken();
        clock.advance(Duration.ofMinutes(1));
        doThrow(new MailSendException("smtp failure")).when(mail).send(any(SimpleMailMessage.class));
        requestReset("reset-third@example.com").andExpect(status().isAccepted());
        confirmReset(first).andExpect(status().isOk());
    }

    private Credentials localRegistration(String username, String email) throws Exception {
        String body = """
                {"username":"%s","email":"%s","firstName":"First","lastName":"Last","password":"a-strong-password"}
                """.formatted(username, email);
        return from(mvc.perform(withCsrf("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn());
    }

    private ResultActions requestReset(String email) throws Exception {
        return mvc.perform(withCsrf("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions confirmReset(String token) throws Exception {
        return mvc.perform(withCsrf("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"new-strong-password\"}"));
    }

    private String sentResetToken() {
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        return tokenFrom(message.getValue());
    }

    private String tokenFrom(SimpleMailMessage message) {
        String text = message.getText();
        return text.substring(text.indexOf("?token=") + 7).strip();
    }

    @Test
    void localRegistrationAndLoginReuseTheSessionFlow() throws Exception {
        String body = """
                {"username":"Éclair","email":"user@example.com","firstName":"First","lastName":"Last","password":"a-strong-password"}
                """;
        var registered = from(mvc.perform(withCsrf("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn());
        me(registered).andExpect(status().isOk());
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, registered.userId());
        assertThat(hash).startsWith("$argon2id$").doesNotContain("a-strong-password");
        var loggedIn = from(mvc.perform(withCsrf("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"e\\u0301CLAIR\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(loggedIn.userId()).isEqualTo(registered.userId());
        me(loggedIn).andExpect(status().isOk());
        mvc.perform(withCsrf("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("user@example.com", "other@example.com").replace("Éclair", "éCLAIR")))
                .andExpect(status().isConflict());
        mvc.perform(withCsrf("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("user@example.com", "USER@example.com").replace("Éclair", "another")))
                .andExpect(status().isConflict());
        mvc.perform(withCsrf("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"éclair\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("invalid_credentials"));
        mvc.perform(withCsrf("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"unknown\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("invalid_credentials"));
        mvc.perform(withCsrf("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("Éclair", "ab")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void localRegistrationRejectsExistingOauthEmail() throws Exception {
        jdbc.update("INSERT INTO users (provider, provider_user_id, email) VALUES ('google', 'existing', 'oauth@example.com')");
        mvc.perform(withCsrf("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"newuser","email":"OAUTH@example.com","firstName":"First","lastName":"Last","password":"a-strong-password"}
                """)).andExpect(status().isConflict());
    }

    @Test
    void newUserCompletesProfileBeforeReceivingSession() throws Exception {
        String code = rawHandoff("google", "new-oauth", null);
        mvc.perform(complete(code)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("profile_required"))
                .andExpect(jsonPath("$.expiresAt").value(START.plus(Duration.ofMinutes(15)).toString()))
                .andExpect(cookie().maxAge("hypertube_login_proof", 900))
                .andExpect(cookie().path("hypertube_login_proof", "/api/auth/login-handoffs"))
                .andExpect(cookie().doesNotExist(AuthCookies.ACCESS));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
        mvc.perform(post("/api/auth/login-handoffs/profile").param("code", code)
                .cookie(new Cookie("hypertube_login_proof", PROOF)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(withCsrf("/api/auth/login-handoffs/profile").param("code", code)
                .cookie(new Cookie("hypertube_login_proof", "b".repeat(64))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newuser\",\"email\":\"new@example.com\",\"firstName\":\"New\",\"lastName\":\"User\"}"))
                .andExpect(status().isUnauthorized());
        var user = from(mvc.perform(profile(code, "newuser", "new@example.com")).andExpect(status().isOk()).andReturn());
        me(user).andExpect(status().isOk());
        mvc.perform(profile(code, "newuser", "new@example.com")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, user.userId())).isNull();
    }

    @Test
    void existingOAuthUserWithIncompleteProfileMustCompleteIt() throws Exception {
        jdbc.update("INSERT INTO users (provider, provider_user_id, display_name) VALUES ('42', 'incomplete', 'Old Name')");
        String code = rawHandoff("42", "incomplete", null);
        mvc.perform(complete(code)).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("profile_required"));
        var user = from(mvc.perform(profile(code, "completeuser", "complete@example.com")).andExpect(status().isOk()).andReturn());
        assertThat(jdbc.queryForObject("SELECT display_name FROM users WHERE id = ?", String.class, user.userId())).isEqualTo("Old Name");
    }

    @Test
    void matchingPasswordAccountRequiresProofAndPreservesProfile() throws Exception {
        var account = localRegistration("link_user", "link@example.com");
        String code = rawHandoff("google", "matching-provider", "LINK@example.com");
        mvc.perform(complete(code)).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("link_required"))
                .andExpect(cookie().maxAge("hypertube_login_proof", 120)).andExpect(cookie().doesNotExist(AuthCookies.ACCESS));
        localRegistration("other_user", "other-user@example.com");
        mvc.perform(link(code, "other_user", "a-strong-password")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login-handoffs/link").param("code", code)
                .cookie(new Cookie("hypertube_login_proof", PROOF)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"link_user\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(link(code, "link_user", "wrong-password")).andExpect(status().isUnauthorized());
        var linked = from(mvc.perform(link(code, "link_user", "a-strong-password")).andExpect(status().isOk()).andReturn());
        assertThat(linked.userId()).isEqualTo(account.userId());
        mvc.perform(link(code, "link_user", "a-strong-password")).andExpect(status().isUnauthorized());
        String next = rawHandoff("google", "matching-provider", "changed@example.com");
        from(mvc.perform(complete(next)).andExpect(status().isOk()).andReturn());
        assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, account.userId())).isEqualTo("link@example.com");
        assertThat(jdbc.queryForObject("SELECT first_name FROM users WHERE id = ?", String.class, account.userId())).isEqualTo("First");
        String other = rawHandoff("42", "other-provider", "link@example.com");
        mvc.perform(complete(other)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Sign in with your original provider or password."));
    }

    @Test
    void profileEmailMatchingPasswordAccountRequiresVerification() throws Exception {
        var account = localRegistration("profile_link", "profile-link@example.com");
        String code = rawHandoff("42", "profile-provider", null);
        mvc.perform(complete(code)).andExpect(status().isAccepted());
        mvc.perform(profile(code, "ignoredname", "profile-link@example.com")).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("link_required"));
        var linked = from(mvc.perform(link(code, "profile_link", "a-strong-password")).andExpect(status().isOk()).andReturn());
        assertThat(linked.userId()).isEqualTo(account.userId());
    }

    @Test
    void deadlinesAndOauthOnlyEmailConflictsAreEnforced() throws Exception {
        String initial = rawHandoff("google", "expired-initial", null);
        clock.advance(Duration.ofMinutes(2));
        mvc.perform(complete(initial)).andExpect(status().isUnauthorized());
        String pending = rawHandoff("google", "expired-profile", null);
        mvc.perform(complete(pending)).andExpect(status().isAccepted());
        clock.advance(Duration.ofMinutes(15));
        mvc.perform(profile(pending, "expired_profile", "expired@example.com")).andExpect(status().isUnauthorized());
        localRegistration("expiry_user", "expiry@example.com");
        String linking = rawHandoff("google", "expired-link", "expiry@example.com");
        mvc.perform(complete(linking)).andExpect(status().isAccepted());
        clock.advance(Duration.ofMinutes(2));
        mvc.perform(link(linking, "expiry_user", "a-strong-password")).andExpect(status().isUnauthorized());
        jdbc.update("INSERT INTO users (provider, provider_user_id, email) VALUES ('google', 'existing-oauth', 'taken@example.com')");
        String conflict = rawHandoff("42", "conflict-provider", "taken@example.com");
        mvc.perform(complete(conflict)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Sign in with your original provider or password."));
    }

    @Test
    void repeatedWrongLinkPasswordsUseExistingLoginRateLimit() throws Exception {
        localRegistration("rate_link", "rate-link@example.com");
        String code = rawHandoff("google", "rate-provider", "rate-link@example.com");
        mvc.perform(complete(code)).andExpect(status().isAccepted());
        for (int i = 0; i < 5; i++) mvc.perform(link(code, "rate_link", "wrong-password")).andExpect(status().isUnauthorized());
        mvc.perform(link(code, "rate_link", "a-strong-password")).andExpect(status().isTooManyRequests());
        assertThat(jdbc.queryForObject("SELECT provider FROM users WHERE username_key = 'rate_link'", String.class)).isNull();
    }

    @Test
    void concurrentUseOfOneLinkHandoffCreatesOneSession() throws Exception {
        localRegistration("single_link", "single-link@example.com");
        String code = rawHandoff("google", "single-provider", "single-link@example.com");
        mvc.perform(complete(code)).andExpect(status().isAccepted());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> request = () -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(link(code, "single_link", "a-strong-password")).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)).stream().sorted().toList())
                    .containsExactly(200, 401);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_sessions", Integer.class)).isEqualTo(2);
    }

    @Test
    void concurrentLinkAttemptsDoNotAttachTwoProviders() throws Exception {
        localRegistration("concurrent_link", "concurrent-link@example.com");
        String firstCode = rawHandoff("google", "race-google", "concurrent-link@example.com");
        String secondCode = rawHandoff("42", "race-42", "concurrent-link@example.com");
        mvc.perform(complete(firstCode)).andExpect(status().isAccepted());
        mvc.perform(complete(secondCode)).andExpect(status().isAccepted());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> first = () -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(link(firstCode, "concurrent_link", "a-strong-password")).andReturn().getResponse().getStatus();
            };
            java.util.concurrent.Callable<Integer> second = () -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(link(secondCode, "concurrent_link", "a-strong-password")).andReturn().getResponse().getStatus();
            };
            var left = executor.submit(first);
            var right = executor.submit(second);
            start.countDown();
            assertThat(List.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS)).stream().sorted().toList())
                    .containsExactly(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_sessions", Integer.class)).isEqualTo(2);
    }

    @Test
    void concurrentProfileCompletionsCreateOnlyOneSession() throws Exception {
        String code = rawHandoff("google", "profile-race", null);
        mvc.perform(complete(code)).andExpect(status().isAccepted());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> request = () -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(profile(code, "race_user", "race@example.com")).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)).stream().sorted().toList())
                    .containsExactly(200, 401);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_sessions", Integer.class)).isEqualTo(1);
    }

    @Test
    void issuesHttpOnlyAccessAndOpaqueRefreshCookies() throws Exception {
        Credentials device = login();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns WHERE table_name = 'user_sessions'
                AND column_name IN ('device_info', 'ip_address', 'created_at', 'last_used_at')
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables WHERE table_name = 'refresh_token_history'
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
                .isEqualTo(jwt.hash(device.refresh().getValue())).isNotEqualTo(device.refresh().getValue());
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
        from(mvc.perform(complete(code)).andExpect(status().isOk()).andReturn());
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
    void oldRefreshTokenIsRejectedWithoutRevokingTheCurrentToken() throws Exception {
        Credentials original = login();
        Credentials renewed = rotate(original);
        refresh(original.refresh()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_refresh"));
        assertThat(revoked(original.id())).isFalse();
        me(renewed).andExpect(status().isOk());
        me(rotate(renewed)).andExpect(status().isOk());
    }

    @Test
    void discardedRefreshTokensCannotAuthenticateOrRevokeAnySession() throws Exception {
        Credentials original = login();
        Credentials other = login();
        Credentials current = rotate(rotate(original));
        refresh(original.refresh()).andExpect(status().isUnauthorized());
        mvc.perform(withCsrf("/api/auth/logout", original.refresh())).andExpect(status().isNoContent());
        assertThat(revoked(current.id())).isFalse();
        me(current).andExpect(status().isOk());
        me(rotate(current)).andExpect(status().isOk());
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
            Credentials winner = from(winners.getFirst());
            assertThat(revoked(original.id())).isFalse();
            me(winner).andExpect(status().isOk());
            me(rotate(winner)).andExpect(status().isOk());
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
    void schedulesCleanupUsingTheConfiguredInterval() {
        assertThat(scheduledTasks.getScheduledTasks().stream().map(ScheduledTask::getTask)
                .filter(FixedDelayTask.class::isInstance).map(FixedDelayTask.class::cast))
                .singleElement().satisfies(task -> {
                    assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofHours(24));
                    assertThat(task.getInitialDelayDuration()).isEqualTo(Duration.ofHours(24));
                });
    }

    @Test
    void cleanupDeletesRevokedRowsWithoutResurrectingAccessJwt() throws Exception {
        Credentials first = rotate(login());
        Credentials second = login();
        mvc.perform(withCsrf("/api/auth/logout", first.refresh())).andExpect(status().isNoContent());
        cleanup.cleanup();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_sessions", Integer.class)).isEqualTo(1);
        me(first).andExpect(status().isUnauthorized());
        me(second).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM revoked_sessions", Integer.class)).isEqualTo(1);
        clock.advance(Duration.ofDays(31));
        cleanup.cleanup();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_sessions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM revoked_sessions", Integer.class)).isZero();
    }

    @Test
    void failedSessionInsertRollsBackHandoffAndUserCreation() throws Exception {
        String code = rawHandoff("google", "rollback-test", null);
        mvc.perform(complete(code)).andExpect(status().isAccepted());
        jdbc.execute("ALTER TABLE user_sessions ADD CONSTRAINT reject_test_session CHECK (false) NOT VALID");
        try { assertThrows(ServletException.class, () -> mvc.perform(profile(code, "rollback_user", "rollback@example.com"))); }
        finally { jdbc.execute("ALTER TABLE user_sessions DROP CONSTRAINT reject_test_session"); }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM oauth_login_handoffs", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
        mvc.perform(profile(code, "rollback_user", "rollback@example.com")).andExpect(status().isOk());
        mvc.perform(profile(code, "rollback_user", "rollback@example.com")).andExpect(status().isUnauthorized());
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
        return from(mvc.perform(complete(createHandoff("session-test"))).andExpect(status().isOk()).andReturn());
    }
    private String createHandoff(String account) throws Exception {
        jdbc.update("""
                INSERT INTO users (provider, provider_user_id, username, username_key, first_name, last_name, email)
                VALUES ('google', ?, 'session_test', 'session_test', 'Session', 'Test', 'session-test@example.com')
                ON CONFLICT (provider, provider_user_id) DO NOTHING
                """, account);
        return rawHandoff("google", account, null);
    }
    private String rawHandoff(String provider, String account, String email) throws Exception {
        String json = email == null ? "null" : "\"" + email + "\"";
        return mvc.perform(post("/internal/auth/login-handoffs").header("Authorization", "Bearer " + SECRET)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"provider":"%s","providerUserId":"%s","displayName":"Test User","email":%s,"proofHash":"%s"}
                        """.formatted(provider, account, json, jwt.hash(PROOF))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    private MockHttpServletRequestBuilder profile(String code, String username, String email) throws Exception {
        return withCsrf("/api/auth/login-handoffs/profile").param("code", code)
                .cookie(new Cookie("hypertube_login_proof", PROOF)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","email":"%s","firstName":"First","lastName":"Last"}
                        """.formatted(username, email));
    }
    private MockHttpServletRequestBuilder link(String code, String username, String password) throws Exception {
        return withCsrf("/api/auth/login-handoffs/link").param("code", code)
                .cookie(new Cookie("hypertube_login_proof", PROOF)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}
                        """.formatted(username, password));
    }
    private MockHttpServletRequestBuilder complete(String code) {
        return post("/api/auth/login-handoffs/complete").param("code", code).cookie(new Cookie("hypertube_login_proof", PROOF));
    }
    private Credentials from(MvcResult result) throws Exception {
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
        assertThat(refresh).isNotNull();
        return new Credentials(access, refresh, claims.userId(), claims.sessionId());
    }
    private Credentials rotate(Credentials old) throws Exception { return from(refresh(old.refresh()).andExpect(status().isOk()).andReturn()); }
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

    @Test
    void profileEmailConfirmationRevokesSessionsAndPreservesDisplayName() throws Exception {
        var user = localRegistration("profile_edit", "profile-old@example.com");
        var csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse();
        String token = JsonPath.read(csrf.getContentAsString(), "$.token");
        mvc.perform(patch("/api/users/" + user.userId()).cookie(csrf.getCookie("XSRF-TOKEN"), new Cookie(AuthCookies.ACCESS, user.access()))
                .header("X-XSRF-TOKEN", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"changed_name\",\"firstName\":\"New\",\"email\":\"profile-new@example.com\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emailChange").value("pending"))
                .andExpect(jsonPath("$.user.email").value("profile-old@example.com"))
                .andExpect(jsonPath("$.user.displayName").value("profile_edit"))
                .andExpect(jsonPath("$.user.username").value("changed_name"));
        String confirmation = sentResetToken();
        mvc.perform(withCsrf("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + confirmation + "\"}")).andExpect(status().isNoContent());
        me(user).andExpect(status().isUnauthorized());
        mvc.perform(withCsrf("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + confirmation + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(withCsrf("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"changed_name\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value("profile-new@example.com"));
    }

    private ResultActions edit(Credentials user, long target, String body) throws Exception {
        var csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse();
        String token = JsonPath.read(csrf.getContentAsString(), "$.token");
        return mvc.perform(patch("/api/users/" + target).cookie(csrf.getCookie("XSRF-TOKEN"), new Cookie(AuthCookies.ACCESS, user.access()))
                .header("X-XSRF-TOKEN", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void failedDeliveryPreservesPreviousTokenAndCommitsOtherEdits() throws Exception {
        var user = localRegistration("delivery_user", "delivery-old@example.com");
        edit(user, user.userId(), "{\"email\":\"delivery-first@example.com\",\"password\":\"a-strong-password\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.emailChange").value("pending"));
        String confirmation = sentResetToken();
        clock.advance(Duration.ofMinutes(1));
        doThrow(new MailSendException("failure")).when(mail).send(any(SimpleMailMessage.class));
        edit(user, user.userId(), "{\"email\":\"delivery-second@example.com\",\"password\":\"a-strong-password\",\"firstName\":\"Saved\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.emailChange").value("delivery_failed"))
                .andExpect(jsonPath("$.user.firstName").value("Saved"));
        mvc.perform(withCsrf("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + confirmation + "\"}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, user.userId())).isEqualTo("delivery-first@example.com");
    }

    @Test
    void invalidProfileAndWrongPasswordSaveNothing() throws Exception {
        var user = localRegistration("invalid_edit", "invalid-edit@example.com");
        var other = localRegistration("another_edit", "another-edit@example.com");
        edit(user, other.userId(), "{\"firstName\":\"Wrong\"}").andExpect(status().isForbidden());
        edit(user, user.userId(), "{\"firstName\":\"Wrong\",\"email\":\"another-edit@example.com\",\"password\":\"a-strong-password\"}")
                .andExpect(status().isConflict());
        verify(mail, never()).send(any(SimpleMailMessage.class));
        edit(user, user.userId(), "{\"firstName\":\"Wrong\",\"email\":\"invalid-new@example.com\",\"password\":\"bad\"}")
                .andExpect(status().isUnauthorized());
        edit(user, user.userId(), "{\"firstName\":\" \",\"lastName\":\"Wrong\"}").andExpect(status().isBadRequest());
        me(user).andExpect(jsonPath("$.firstName").value("First")).andExpect(jsonPath("$.lastName").value("Last"));
    }

    @Test
    void oauthReauthIsBoundToBrowserProviderAndSessionAndConsumedOnce() throws Exception {
        var user = login();
        var second = from(mvc.perform(complete(rawHandoff("google", "session-test", null))).andExpect(status().isOk()).andReturn());
        var start = mvc.perform(withCsrf("/api/auth/reauth/start").cookie(new Cookie(AuthCookies.ACCESS, user.access())))
                .andExpect(status().isOk()).andReturn().getResponse();
        String challenge = JsonPath.read(start.getContentAsString(), "$.challenge");
        Cookie proof = start.getCookie("hypertube_reauth");
        assertThat(proof).isNotNull();
        String callback = "{\"challenge\":\"%s\",\"secret\":\"%s\",\"provider\":\"google\",\"providerUserId\":\"%s\"}";
        mvc.perform(post("/internal/auth/reauth/callback").header("Authorization", "Bearer " + SECRET)
                .contentType(MediaType.APPLICATION_JSON).content(callback.formatted(challenge, proof.getValue(), "wrong")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/auth/reauth/callback").header("Authorization", "Bearer " + SECRET)
                .contentType(MediaType.APPLICATION_JSON).content(callback.formatted(challenge, "wrong", "session-test")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/auth/reauth/callback").header("Authorization", "Bearer " + SECRET)
                .contentType(MediaType.APPLICATION_JSON).content(callback.formatted(challenge, proof.getValue(), "session-test")))
                .andExpect(status().isNoContent());
        var csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse();
        String csrfToken = JsonPath.read(csrf.getContentAsString(), "$.token");
        String body = "{\"email\":\"oauth-new@example.com\"}";
        mvc.perform(patch("/api/users/" + user.userId()).cookie(csrf.getCookie("XSRF-TOKEN"), new Cookie(AuthCookies.ACCESS, second.access()), proof)
                .header("X-XSRF-TOKEN", csrfToken).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/users/" + user.userId()).cookie(csrf.getCookie("XSRF-TOKEN"), new Cookie(AuthCookies.ACCESS, user.access()), proof)
                .header("X-XSRF-TOKEN", csrfToken).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(patch("/api/users/" + user.userId()).cookie(csrf.getCookie("XSRF-TOKEN"), new Cookie(AuthCookies.ACCESS, user.access()), proof)
                .header("X-XSRF-TOKEN", csrfToken).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
    }

    @Test
    void competingEmailClaimsAreResolvedAtConfirmation() throws Exception {
        var first = localRegistration("claim_first", "claim-first@example.com");
        var second = localRegistration("claim_second", "claim-second@example.com");
        String body = "{\"email\":\"claimed@example.com\",\"password\":\"a-strong-password\"}";
        edit(first, first.userId(), body).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(1));
        edit(second, second.userId(), body).andExpect(status().isOk());
        var messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(2)).send(messages.capture());
        var tokens = messages.getAllValues().stream().map(this::tokenFrom).toList();
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.function.Function<String, java.util.concurrent.Callable<Integer>> confirm = token -> () -> {
                assertThat(gate.await(10, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(withCsrf("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}")).andReturn().getResponse().getStatus();
            };
            var a = executor.submit(confirm.apply(tokens.get(0)));
            var b = executor.submit(confirm.apply(tokens.get(1)));
            gate.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)).stream().sorted().toList())
                    .containsExactly(204, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE lower(email) = 'claimed@example.com'", Integer.class)).isEqualTo(1);
    }

    @Test
    void confirmationInvalidatesResetTokensAndDoesNotClearUnrelatedCookies() throws Exception {
        var user = localRegistration("reset_profile", "reset-profile@example.com");
        var other = localRegistration("unrelated_profile", "unrelated-profile@example.com");
        requestReset("reset-profile@example.com").andExpect(status().isAccepted());
        edit(user, user.userId(), "{\"email\":\"reset-profile-new@example.com\",\"password\":\"a-strong-password\"}").andExpect(status().isOk());
        var messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(2)).send(messages.capture());
        String resetToken = tokenFrom(messages.getAllValues().getFirst());
        String emailToken = tokenFrom(messages.getAllValues().getLast());
        mvc.perform(withCsrf("/api/auth/email-change/confirm", new Cookie(AuthCookies.ACCESS, other.access()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + emailToken + "\"}"))
                .andExpect(status().isNoContent()).andExpect(cookie().doesNotExist(AuthCookies.ACCESS))
                .andExpect(cookie().doesNotExist(AuthCookies.REFRESH));
        confirmReset(resetToken).andExpect(status().isUnauthorized());
        me(other).andExpect(status().isOk());
    }

    @Test
    void emailConfirmationExpiresAfterThirtyMinutes() throws Exception {
        var user = localRegistration("expiry_profile", "expiry-profile@example.com");
        edit(user, user.userId(), "{\"email\":\"expiry-profile-new@example.com\",\"password\":\"a-strong-password\"}").andExpect(status().isOk());
        String token = sentResetToken();
        clock.advance(Duration.ofMinutes(30));
        mvc.perform(withCsrf("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, user.userId())).isEqualTo("expiry-profile@example.com");
    }

    @Test
    void concurrentEmailConfirmationsConsumeTheLinkOnlyOnce() throws Exception {
        var user = localRegistration("email_race", "email-race@example.com");
        edit(user, user.userId(), "{\"email\":\"email-race-new@example.com\",\"password\":\"a-strong-password\"}").andExpect(status().isOk());
        String token = sentResetToken();
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> confirm = () -> {
                assertThat(gate.await(10, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(withCsrf("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}")).andReturn().getResponse().getStatus();
            };
            var a = executor.submit(confirm);
            var b = executor.submit(confirm);
            gate.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)).stream().sorted().toList())
                    .containsExactly(204, 401);
        }
    }

    @Test
    void emailRequestsAreLimitedPerUserAndDestination() throws Exception {
        var first = localRegistration("limit_first", "limit-first@example.com");
        var second = localRegistration("limit_second", "limit-second@example.com");
        edit(first, first.userId(), "{\"email\":\"limit-new@example.com\",\"password\":\"a-strong-password\"}").andExpect(status().isOk());
        edit(first, first.userId(), "{\"email\":\"limit-other@example.com\",\"password\":\"a-strong-password\"}").andExpect(status().isTooManyRequests());
        edit(second, second.userId(), "{\"email\":\"LIMIT-NEW@example.com\",\"password\":\"a-strong-password\"}").andExpect(status().isTooManyRequests());
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void usernameConflictsAreNormalizedAndDoNotSaveOtherEdits() throws Exception {
        var first = localRegistration("unique_first", "unique-first@example.com");
        localRegistration("unique_second", "unique-second@example.com");
        edit(first, first.userId(), "{\"username\":\"UNIQUE_SECOND\",\"firstName\":\"Wrong\"}")
                .andExpect(status().isConflict());
        me(first).andExpect(jsonPath("$.username").value("unique_first")).andExpect(jsonPath("$.firstName").value("First"));
    }

    @Test
    void passwordReauthenticationUsesLoginRateLimits() throws Exception {
        var user = localRegistration("reauth_limit", "reauth-limit@example.com");
        String body = "{\"firstName\":\"Wrong\",\"email\":\"reauth-limit-new@example.com\",\"password\":\"bad\"}";
        for (int i = 0; i < 5; i++) edit(user, user.userId(), body).andExpect(status().isUnauthorized());
        edit(user, user.userId(), "{\"email\":\"reauth-limit-new@example.com\",\"password\":\"a-strong-password\"}")
                .andExpect(status().isTooManyRequests());
        me(user).andExpect(jsonPath("$.firstName").value("First"));
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    private void awaitWaitingQuery(String fragment) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND lower(query) LIKE lower(?)",
                    Integer.class, "%" + fragment + "%");
            if (count != null && count > 0) return;
            Thread.sleep(10);
        }
        throw new AssertionError("Expected database lock wait for " + fragment);
    }

    @Test
    void confirmationWaitsForConcurrentLoginAndRevokesItsSession() throws Exception {
        var user = localRegistration("login_race", "login-race@example.com");
        edit(user, user.userId(), "{\"email\":\"login-race-new@example.com\",\"password\":\"a-strong-password\"}").andExpect(status().isOk());
        String token = sentResetToken();
        try (var executor = Executors.newFixedThreadPool(2); var connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, user.userId()); lock.executeQuery();
            }
            var login = executor.submit(() -> mvc.perform(withCsrf("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"login_race\",\"password\":\"a-strong-password\"}")).andReturn());
            awaitWaitingQuery("SELECT id, password_hash FROM users");
            var confirm = executor.submit(() -> mvc.perform(withCsrf("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"" + token + "\"}")).andReturn());
            awaitWaitingQuery("FROM users u JOIN email_change_tokens");
            connection.commit();
            var created = from(login.get(15, TimeUnit.SECONDS));
            assertThat(confirm.get(15, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(204);
            me(user).andExpect(status().isUnauthorized());
            me(created).andExpect(status().isUnauthorized());
            refresh(created.refresh()).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void confirmationWaitsForConcurrentPasswordResetAndRevokesItsSession() throws Exception {
        var user = localRegistration("reset_race_profile", "reset-race-profile@example.com");
        requestReset("reset-race-profile@example.com").andExpect(status().isAccepted());
        edit(user, user.userId(), "{\"email\":\"reset-race-profile-new@example.com\",\"password\":\"a-strong-password\"}").andExpect(status().isOk());
        var messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(2)).send(messages.capture());
        String resetToken = tokenFrom(messages.getAllValues().getFirst());
        String emailToken = tokenFrom(messages.getAllValues().getLast());
        try (var executor = Executors.newFixedThreadPool(2); var connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, user.userId()); lock.executeQuery();
            }
            var reset = executor.submit(() -> confirmReset(resetToken).andReturn());
            awaitWaitingQuery("FROM users u JOIN password_reset_tokens");
            var confirm = executor.submit(() -> mvc.perform(withCsrf("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"" + emailToken + "\"}")).andReturn());
            awaitWaitingQuery("FROM users u JOIN email_change_tokens");
            connection.commit();
            var created = from(reset.get(15, TimeUnit.SECONDS));
            assertThat(confirm.get(15, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(204);
            me(user).andExpect(status().isUnauthorized());
            me(created).andExpect(status().isUnauthorized());
            refresh(created.refresh()).andExpect(status().isUnauthorized());
            confirmReset(resetToken).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void confirmationWaitsForConcurrentRefreshAndRevokesRotatedCredentials() throws Exception {
        var user = localRegistration("refresh_race", "refresh-race@example.com");
        edit(user, user.userId(), "{\"email\":\"refresh-race-new@example.com\",\"password\":\"a-strong-password\"}").andExpect(status().isOk());
        String token = sentResetToken();
        try (var executor = Executors.newFixedThreadPool(2); var connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("SELECT id FROM user_sessions WHERE id = ? FOR UPDATE")) {
                lock.setObject(1, user.id()); lock.executeQuery();
            }
            var rotating = executor.submit(() -> refresh(user.refresh()).andReturn());
            awaitWaitingQuery("user_sessions");
            var confirm = executor.submit(() -> mvc.perform(withCsrf("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"" + token + "\"}")).andReturn());
            awaitWaitingQuery("SELECT id FROM user_sessions WHERE user_id");
            connection.commit();
            var rotated = from(rotating.get(15, TimeUnit.SECONDS));
            assertThat(confirm.get(15, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(204);
            me(user).andExpect(status().isUnauthorized());
            me(rotated).andExpect(status().isUnauthorized());
            refresh(rotated.refresh()).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void confirmationAndConcurrentLogoutCompleteWithoutDeadlock() throws Exception {
        var user = localRegistration("logout_race", "logout-race@example.com");
        edit(user, user.userId(), "{\"email\":\"logout-race-new@example.com\",\"password\":\"a-strong-password\"}").andExpect(status().isOk());
        String token = sentResetToken();
        try (var executor = Executors.newFixedThreadPool(2); var connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("SELECT id FROM user_sessions WHERE id = ? FOR UPDATE")) {
                lock.setObject(1, user.id()); lock.executeQuery();
            }
            var logout = executor.submit(() -> mvc.perform(withCsrf("/api/auth/logout", user.refresh())
                    .cookie(new Cookie(AuthCookies.ACCESS, user.access()))).andReturn());
            awaitWaitingQuery("user_sessions");
            var confirm = executor.submit(() -> mvc.perform(withCsrf("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"" + token + "\"}")).andReturn());
            awaitWaitingQuery("SELECT id FROM user_sessions WHERE user_id");
            connection.commit();
            assertThat(logout.get(15, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(204);
            assertThat(confirm.get(15, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(204);
            me(user).andExpect(status().isUnauthorized());
            refresh(user.refresh()).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void cleanupSkipsLockedRevokedSessionDuringConfirmation() throws Exception {
        var old = localRegistration("cleanup_race", "cleanup-race@example.com");
        var active = from(mvc.perform(withCsrf("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"cleanup_race\",\"password\":\"a-strong-password\"}")).andReturn());
        mvc.perform(withCsrf("/api/auth/logout", old.refresh()).cookie(new Cookie(AuthCookies.ACCESS, old.access())))
                .andExpect(status().isNoContent());
        edit(active, active.userId(), "{\"email\":\"cleanup-race-new@example.com\",\"password\":\"a-strong-password\"}").andExpect(status().isOk());
        String token = sentResetToken();
        try (var executor = Executors.newFixedThreadPool(2); var connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("SELECT id FROM user_sessions WHERE id = ? FOR UPDATE")) {
                lock.setObject(1, old.id()); lock.executeQuery();
            }
            var confirm = executor.submit(() -> mvc.perform(withCsrf("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"" + token + "\"}")).andReturn());
            awaitWaitingQuery("SELECT id FROM user_sessions WHERE user_id");
            executor.submit(cleanup::cleanup).get(15, TimeUnit.SECONDS);
            connection.commit();
            assertThat(confirm.get(15, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(204);
            me(old).andExpect(status().isUnauthorized());
            me(active).andExpect(status().isUnauthorized());
            cleanup.cleanup();
            me(old).andExpect(status().isUnauthorized());
            me(active).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void oauthReauthenticationExpiresAfterTwoMinutes() throws Exception {
        var user = login();
        var start = mvc.perform(withCsrf("/api/auth/reauth/start").cookie(new Cookie(AuthCookies.ACCESS, user.access())))
                .andExpect(status().isOk()).andReturn().getResponse();
        String challenge = JsonPath.read(start.getContentAsString(), "$.challenge");
        String proof = start.getCookie("hypertube_reauth").getValue();
        clock.advance(Duration.ofMinutes(2));
        mvc.perform(post("/internal/auth/reauth/callback").header("Authorization", "Bearer " + SECRET)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"challenge":"%s","secret":"%s","provider":"google","providerUserId":"session-test"}
                        """.formatted(challenge, proof))).andExpect(status().isUnauthorized());
    }

    @Test
    void newestDeliveredConfirmationInvalidatesThePreviousLink() throws Exception {
        var user = localRegistration("newest_email", "newest-email@example.com");
        edit(user, user.userId(), "{\"email\":\"newest-first@example.com\",\"password\":\"a-strong-password\"}").andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(1));
        edit(user, user.userId(), "{\"email\":\"newest-second@example.com\",\"password\":\"a-strong-password\"}").andExpect(status().isOk());
        var messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(2)).send(messages.capture());
        String previous = tokenFrom(messages.getAllValues().getFirst());
        String current = tokenFrom(messages.getAllValues().getLast());
        mvc.perform(withCsrf("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + previous + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(withCsrf("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + current + "\"}")).andExpect(status().isNoContent());
    }

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
