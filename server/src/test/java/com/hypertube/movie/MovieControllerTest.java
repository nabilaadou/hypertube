package com.hypertube.movie;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MovieControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MovieRepository movies;

    @Test
    void listsMovieIdsAndNamesInStableOrder() throws Exception {
        Movie zulu = movies.saveAndFlush(new Movie("Zulu", null, null, null, Set.of(), 0));
        Movie arrival = movies.saveAndFlush(new Movie("Arrival", 8.0, 2016, 116, Set.of("en"), 2));

        mockMvc.perform(get("/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(arrival.getId()))
                .andExpect(jsonPath("$[0].name").value("Arrival"))
                .andExpect(jsonPath("$[1].id").value(zulu.getId()))
                .andExpect(jsonPath("$[1].name").value("Zulu"))
                .andExpect(jsonPath("$[0].rating").doesNotExist());
    }

    @Test
    void returnsStoredMetadataAndSubtitles() throws Exception {
        Movie movie = movies.saveAndFlush(
                new Movie("Arrival", 8.0, 2016, 116, Set.of("fr", "en"), 7));

        mockMvc.perform(get("/movies/{id}", movie.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(movie.getId()))
                .andExpect(jsonPath("$.name").value("Arrival"))
                .andExpect(jsonPath("$.rating").value(8.0))
                .andExpect(jsonPath("$.year").value(2016))
                .andExpect(jsonPath("$.runtime").value(116))
                .andExpect(jsonPath("$.availableSubtitles", hasSize(2)))
                .andExpect(jsonPath("$.availableSubtitles[0]").value("en"))
                .andExpect(jsonPath("$.availableSubtitles[1]").value("fr"))
                .andExpect(jsonPath("$.commentCount").value(7));
    }

    @Test
    void returnsConsistentNullsAndEmptySubtitles() throws Exception {
        Movie movie = movies.saveAndFlush(new Movie("Untitled", null, null, null, Set.of(), 0));

        mockMvc.perform(get("/movies/{id}", movie.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(nullValue()))
                .andExpect(jsonPath("$.year").value(nullValue()))
                .andExpect(jsonPath("$.runtime").value(nullValue()))
                .andExpect(jsonPath("$.availableSubtitles", hasSize(0)))
                .andExpect(jsonPath("$.commentCount").value(0));
    }

    @Test
    void returns404ForUnknownMovie() throws Exception {
        mockMvc.perform(get("/movies/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsEmptyListWhenCatalogIsEmpty() throws Exception {
        mockMvc.perform(get("/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
