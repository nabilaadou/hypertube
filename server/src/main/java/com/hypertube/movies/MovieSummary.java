package com.hypertube.movies;

import java.time.LocalDate;

public record MovieSummary(int tmdbId, String title, LocalDate releaseDate, String posterUrl) {
}
