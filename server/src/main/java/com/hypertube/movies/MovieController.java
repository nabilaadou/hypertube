package com.hypertube.movies;

import com.hypertube.movies.tmdb.TmdbClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final TmdbClient tmdbClient;

    MovieController(TmdbClient tmdbClient) {
        this.tmdbClient = tmdbClient;
    }

    @GetMapping("/search")
    public MoviePage search(@RequestParam String q, @RequestParam(defaultValue = "1") int page) {
        String query = q.strip();
        if (query.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a movie title to search.");
        }
        if (page < 1 || page > TmdbClient.MAX_SEARCH_PAGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Page must be between 1 and " + TmdbClient.MAX_SEARCH_PAGE + ".");
        }
        return tmdbClient.search(query, page);
    }

    @GetMapping("/{tmdbId}")
    public MovieDetails details(@PathVariable int tmdbId) {
        if (tmdbId < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Movie ID must be positive.");
        }
        return tmdbClient.details(tmdbId);
    }

    @ExceptionHandler(TmdbClient.Failure.class)
    ProblemDetail handleCatalogFailure(TmdbClient.Failure failure) {
        return switch (failure.reason()) {
            case MOVIE_NOT_FOUND -> ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Movie not found.");
            case UNAVAILABLE -> ProblemDetail.forStatusAndDetail(
                    HttpStatus.SERVICE_UNAVAILABLE, "Movie metadata is temporarily unavailable.");
            case INVALID_RESPONSE -> ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_GATEWAY, "Movie metadata provider returned an invalid response.");
        };
    }
}
