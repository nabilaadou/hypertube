package com.hypertube.movie;

import java.util.List;

public record MovieDetails(
        Long id,
        String name,
        Double rating,
        Integer year,
        Integer runtime,
        List<String> availableSubtitles,
        long commentCount) {
}
