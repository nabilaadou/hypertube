package com.hypertube.movie;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class MovieService {

    private final MovieRepository movies;

    public MovieService(MovieRepository movies) {
        this.movies = movies;
    }

    public List<MovieSummary> listMovies() {
        return movies.findAll(Sort.by("name", "id")).stream()
                .map(movie -> new MovieSummary(movie.getId(), movie.getName()))
                .toList();
    }

    public MovieDetails getMovie(long id) {
        Movie movie = movies.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found"));

        return new MovieDetails(
                movie.getId(),
                movie.getName(),
                movie.getRating(),
                movie.getYear(),
                movie.getRuntime(),
                movie.getAvailableSubtitles().stream().sorted().toList(),
                movie.getCommentCount());
    }
}
