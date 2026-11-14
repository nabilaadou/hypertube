package com.hypertube.movie;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "movies")
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String name;

    private Double rating;

    @Column(name = "release_year")
    private Integer year;

    @Column(name = "runtime_minutes")
    private Integer runtime;

    @Column(name = "comment_count", nullable = false)
    private long commentCount;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "movie_subtitles", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "language", nullable = false)
    private Set<String> availableSubtitles = new HashSet<>();

    protected Movie() {
        // Used by JPA.
    }

    public Movie(String name, Double rating, Integer year, Integer runtime,
                 Set<String> availableSubtitles, long commentCount) {
        this.name = name;
        this.rating = rating;
        this.year = year;
        this.runtime = runtime;
        this.availableSubtitles = new HashSet<>(availableSubtitles);
        this.commentCount = commentCount;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Double getRating() {
        return rating;
    }

    public Integer getYear() {
        return year;
    }

    public Integer getRuntime() {
        return runtime;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public Set<String> getAvailableSubtitles() {
        return availableSubtitles;
    }
}
