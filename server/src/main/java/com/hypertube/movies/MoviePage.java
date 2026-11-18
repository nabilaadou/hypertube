package com.hypertube.movies;

import java.util.List;

public record MoviePage(int page, int totalPages, int totalResults, List<MovieSummary> results) {
}
