package com.hypertube.movies;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "hypertube.tmdb.read-access-token=test-token")
@AutoConfigureMockMvc
class MovieControllerTest {

    private static final AtomicReference<UpstreamResponse> RESPONSE = new AtomicReference<>();
    private static final Queue<UpstreamRequest> REQUESTS = new ConcurrentLinkedQueue<>();
    private static final HttpServer TMDB = startTmdbStub();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configureTmdb(DynamicPropertyRegistry properties) {
        properties.add("hypertube.tmdb.base-url", () -> "http://127.0.0.1:" + TMDB.getAddress().getPort() + "/3");
    }

    @BeforeEach
    void resetTmdbStub() {
        RESPONSE.set(new UpstreamResponse(500, ""));
        REQUESTS.clear();
    }

    @AfterAll
    static void stopTmdbStub() {
        TMDB.stop(0);
    }

    @Test
    void searchesAndReturnsHypertubeMovieCards() throws Exception {
        replyWith(200, """
                {
                  "page": 2, "total_pages": 4, "total_results": 65,
                  "results": [{
                    "id": 348, "title": "Alien", "release_date": "1979-05-25",
                    "poster_path": "/alien.jpg", "original_language": "en"
                  }]
                }
                """);

        mockMvc.perform(get("/api/movies/search").param("q", "  A+B & É?%  ").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalPages").value(4))
                .andExpect(jsonPath("$.totalResults").value(65))
                .andExpect(jsonPath("$.results[0].tmdbId").value(348))
                .andExpect(jsonPath("$.results[0].title").value("Alien"))
                .andExpect(jsonPath("$.results[0].releaseDate").value("1979-05-25"))
                .andExpect(jsonPath("$.results[0].posterUrl").value("https://image.tmdb.org/t/p/w500/alien.jpg"))
                .andExpect(jsonPath("$.results[0].original_language").doesNotExist());

        assertThat(REQUESTS).containsExactly(new UpstreamRequest(
                URI.create("/3/search/movie?query=A%2BB%20%26%20%C3%89%3F%25&page=2"), "Bearer test-token"));
    }

    @Test
    void returnsAnEmptyPageWhenThereAreNoMatches() throws Exception {
        replyWith(200, """
                {"page": 1, "total_pages": 0, "total_results": 0, "results": []}
                """);

        mockMvc.perform(get("/api/movies/search").param("q", "no matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.results").isEmpty());

        assertThat(REQUESTS).containsExactly(new UpstreamRequest(
                URI.create("/3/search/movie?query=no%20matches&page=1"), "Bearer test-token"));
    }

    @Test
    void opensTheSelectedMovie() throws Exception {
        replyWith(200, """
                {
                  "id": 348, "title": "Alien", "release_date": "1979-05-25",
                  "poster_path": "/alien.jpg", "backdrop_path": "/spaceship.jpg",
                  "overview": "A crew encounters a deadly creature.", "runtime": 117,
                  "genres": [{"id": 27, "name": "Horror"}]
                }
                """);

        mockMvc.perform(get("/api/movies/348"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tmdbId").value(348))
                .andExpect(jsonPath("$.synopsis").value("A crew encounters a deadly creature."))
                .andExpect(jsonPath("$.genres[0]").value("Horror"))
                .andExpect(jsonPath("$.runtimeMinutes").value(117))
                .andExpect(jsonPath("$.backdropUrl").value("https://image.tmdb.org/t/p/w1280/spaceship.jpg"));

        assertThat(REQUESTS).containsExactly(new UpstreamRequest(URI.create("/3/movie/348"), "Bearer test-token"));
    }

    @Test
    void allowsUnavailableArtworkAndMetadata() throws Exception {
        replyWith(200, """
                {"id": 348, "title": "Alien", "release_date": "", "poster_path": null}
                """);

        mockMvc.perform(get("/api/movies/348"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Alien"))
                .andExpect(jsonPath("$.releaseDate").value((Object) null))
                .andExpect(jsonPath("$.posterUrl").value((Object) null))
                .andExpect(jsonPath("$.runtimeMinutes").value((Object) null))
                .andExpect(jsonPath("$.genres").isEmpty());
    }

    @Test
    void rejectsInvalidRequestsBeforeCallingTheProvider() throws Exception {
        mockMvc.perform(get("/api/movies/search"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/movies/search").param("q", "   "))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/movies/search").param("q", "Alien").param("page", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/movies/search").param("q", "Alien").param("page", "501"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/movies/search").param("q", "Alien").param("page", "first"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/movies/0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/movies/not-a-number"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/movies/2147483648"))
                .andExpect(status().isBadRequest());

        assertThat(REQUESTS).isEmpty();
    }

    @Test
    void reportsOnlySearchPagesTheApiCanFetch() throws Exception {
        replyWith(200, """
                {"page": 500, "total_pages": 650, "total_results": 13000, "results": []}
                """);

        mockMvc.perform(get("/api/movies/search").param("q", "Alien").param("page", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(500))
                .andExpect(jsonPath("$.totalPages").value(500));
    }

    @Test
    void returnsNotFoundOnlyForMissingMovieDetails() throws Exception {
        replyWith(404, """
                {"status_message": "private provider message"}
                """);

        mockMvc.perform(get("/api/movies/123456"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Movie not found."));
        mockMvc.perform(get("/api/movies/search").param("q", "Alien"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(not(containsString("private provider message"))));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 502})
    void reportsProviderOutagesWithoutExposingProviderErrors(int upstreamStatus) throws Exception {
        replyWith(upstreamStatus, """
                {"status_message": "private provider message"}
                """);

        mockMvc.perform(get("/api/movies/search").param("q", "Alien"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("private provider message"))));
    }

    @Test
    void rejectsAProviderResponseWithMissingRequiredFields() throws Exception {
        replyWith(200, """
                {"page": 1, "total_pages": 1, "total_results": 1, "results": [{"title": "Alien"}]}
                """);

        mockMvc.perform(get("/api/movies/search").param("q", "Alien"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void rejectsMalformedProviderJson() throws Exception {
        replyWith(200, "{invalid json");

        mockMvc.perform(get("/api/movies/search").param("q", "Alien"))
                .andExpect(status().isBadGateway());
    }

    private static void replyWith(int status, String body) {
        RESPONSE.set(new UpstreamResponse(status, body));
    }

    private static HttpServer startTmdbStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", MovieControllerTest::respondToTmdbRequest);
            server.start();
            return server;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void respondToTmdbRequest(HttpExchange exchange) throws IOException {
        try (exchange) {
            REQUESTS.add(new UpstreamRequest(exchange.getRequestURI(),
                    exchange.getRequestHeaders().getFirst("Authorization")));
            UpstreamResponse response = RESPONSE.get();
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private record UpstreamRequest(URI uri, String authorization) {
    }

    private record UpstreamResponse(int status, String body) {
    }
}
