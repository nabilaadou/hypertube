package com.hypertube.movies.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hypertube.movies.MovieDetails;
import com.hypertube.movies.MoviePage;
import com.hypertube.movies.MovieSummary;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TmdbClient {

    public static final int MAX_SEARCH_PAGE = 500;

    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/";

    private final RestClient restClient;
    private final String readAccessToken;

    TmdbClient(
            @Value("${hypertube.tmdb.base-url}") String baseUrl,
            @Value("${hypertube.tmdb.read-access-token}") String readAccessToken) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.readAccessToken = readAccessToken.strip();
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultStatusHandler(status -> !status.is2xxSuccessful(), (request, response) -> {
                    HttpStatusCode status = response.getStatusCode();
                    if (status.is5xxServerError() || status.value() == 401 || status.value() == 403
                            || status.value() == 408 || status.value() == 429) {
                        throw new Failure(Reason.UNAVAILABLE);
                    }
                    throw new Failure(Reason.INVALID_RESPONSE);
                })
                .build();
    }

    public MoviePage search(String query, int page) {
        TmdbPage response = readBody(
                get("/search/movie?query={query}&page={page}", query, page), TmdbPage.class);
        return new MoviePage(
                required(response.page()),
                Math.min(required(response.totalPages()), MAX_SEARCH_PAGE),
                required(response.totalResults()),
                required(response.results()).stream()
                        .map(TmdbClient::required)
                        .map(TmdbMovie::toSummary)
                        .toList());
    }

    public MovieDetails details(int tmdbId) {
        RestClient.ResponseSpec request = get("/movie/{id}", tmdbId)
                .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (outbound, response) -> {
                    throw new Failure(Reason.MOVIE_NOT_FOUND);
                });
        TmdbMovie movie = readBody(request, TmdbMovie.class);
        if (movie.requiredId() != tmdbId) {
            throw new Failure(Reason.INVALID_RESPONSE);
        }
        return movie.toDetails();
    }

    private RestClient.ResponseSpec get(String uri, Object... variables) {
        if (readAccessToken.isBlank()) {
            throw new Failure(Reason.UNAVAILABLE);
        }
        return restClient.get()
                .uri(uri, variables)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(readAccessToken))
                .retrieve();
    }

    private static <T> T readBody(RestClient.ResponseSpec request, Class<T> responseType) {
        try {
            return required(request.body(responseType));
        } catch (ResourceAccessException failure) {
            throw new Failure(Reason.UNAVAILABLE);
        } catch (RestClientException failure) {
            throw new Failure(Reason.INVALID_RESPONSE);
        }
    }

    private static <T> T required(T value) {
        if (value == null) {
            throw new Failure(Reason.INVALID_RESPONSE);
        }
        return value;
    }

    private static LocalDate parseReleaseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException failure) {
            throw new Failure(Reason.INVALID_RESPONSE);
        }
    }

    private static String imageUrl(String size, String path) {
        return path == null || path.isBlank() ? null : IMAGE_BASE_URL + size + path;
    }

    public enum Reason {
        MOVIE_NOT_FOUND,
        UNAVAILABLE,
        INVALID_RESPONSE
    }

    public static final class Failure extends RuntimeException {

        private final Reason reason;

        private Failure(Reason reason) {
            super(reason.name());
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TmdbPage(
            Integer page,
            @JsonProperty("total_pages") Integer totalPages,
            @JsonProperty("total_results") Integer totalResults,
            List<TmdbMovie> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TmdbMovie(
            Integer id,
            String title,
            @JsonProperty("release_date") String releaseDate,
            @JsonProperty("poster_path") String posterPath,
            String overview,
            List<TmdbGenre> genres,
            Integer runtime,
            @JsonProperty("backdrop_path") String backdropPath) {

        int requiredId() {
            if (required(id) < 1) {
                throw new Failure(Reason.INVALID_RESPONSE);
            }
            return id;
        }

        MovieSummary toSummary() {
            return new MovieSummary(requiredId(), required(title), parseReleaseDate(releaseDate),
                    imageUrl("w500", posterPath));
        }

        MovieDetails toDetails() {
            List<String> genreNames = genres == null ? List.of()
                    : genres.stream().map(TmdbClient::required).map(TmdbGenre::name).map(TmdbClient::required).toList();
            return new MovieDetails(requiredId(), required(title), parseReleaseDate(releaseDate),
                    imageUrl("w500", posterPath), overview, genreNames, runtime, imageUrl("w1280", backdropPath));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TmdbGenre(String name) {
    }
}
