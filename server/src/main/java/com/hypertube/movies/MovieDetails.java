package com.hypertube.movies;

import java.time.LocalDate;
import java.util.List;

public record MovieDetails(
        int tmdbId,
        String title,
        LocalDate releaseDate,
        String posterUrl,
        String synopsis,
        List<String> genres,
        Integer runtimeMinutes,
        String backdropUrl) {
}
