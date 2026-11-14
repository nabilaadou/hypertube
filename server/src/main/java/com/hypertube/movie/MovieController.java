package com.hypertube.movie;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/movies")
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping
    public List<MovieSummary> listMovies() {
        return movieService.listMovies();
    }

    @GetMapping("/{id}")
    public MovieDetails getMovie(@PathVariable long id) {
        return movieService.getMovie(id);
    }
}
