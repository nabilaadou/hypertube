package com.hypertube.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "TEST_MAILPIT_ENABLED", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DATABASE_URL}",
        "spring.datasource.username=${TEST_DATABASE_USER:postgres}",
        "spring.datasource.password=${TEST_DATABASE_PASSWORD:}",
        "spring.mail.host=localhost", "spring.mail.port=11025",
        "hypertube.auth.handoff-secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "hypertube.auth.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg=="
})
@AutoConfigureMockMvc
class MailpitIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void sendsResetLinkToMailpitAndUsesItToSignIn() throws Exception {
        jdbc.update("DELETE FROM users");
        mvc.perform(withCsrf("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"mailtest","email":"mailtest@example.com","firstName":"Mail","lastName":"Test","password":"original-password"}
                """)).andExpect(status().isOk());
        mvc.perform(withCsrf("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"mailtest@example.com\"}")).andExpect(status().isAccepted());
        var http = HttpClient.newHttpClient();
        String inbox = http.send(HttpRequest.newBuilder(URI.create("http://localhost:18025/api/v1/messages")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        String id = JsonPath.read(inbox, "$.messages[0].ID");
        String message = http.send(HttpRequest.newBuilder(URI.create("http://localhost:18025/api/v1/message/" + id)).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        String text = JsonPath.read(message, "$.Text");
        assertThat(text).contains("http://localhost:3000/reset-password?token=");
        String token = text.substring(text.indexOf("?token=") + 7).strip();
        mvc.perform(withCsrf("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"replacement-password\"}"))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder withCsrf(String path) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse();
        String token = JsonPath.read(response.getContentAsString(), "$.token");
        return post(path).cookie(response.getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", token);
    }
}
