package dev.pmtd.agent.catalog

import java.time.Instant
import java.util.UUID

enum class TitleKind(val databaseValue: String) {
    MOVIE("movie"), SERIES("series"), OTHER("other"), UNKNOWN("unknown");

    companion object {
        fun fromDatabase(value: String): TitleKind = entries.single { it.databaseValue == value }
    }
}

enum class ExternalIdNamespace(val databaseValue: String) {
    IMDB_TITLE("imdb_title"), TMDB_MOVIE("tmdb_movie"), TMDB_TV("tmdb_tv"), TVMAZE_SHOW("tvmaze_show");
}

data class ExternalTitleId(val namespace: ExternalIdNamespace, val value: String) {
    init {
        require(value.isNotBlank()) { "External title ID must not be blank" }
        require(namespace != ExternalIdNamespace.IMDB_TITLE || value.matches(Regex("tt[0-9]+"))) {
            "Invalid IMDb title ID"
        }
    }
}

data class CatalogTitle(
    val id: UUID,
    val displayTitle: String,
    val kind: TitleKind,
    val releaseYear: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(displayTitle.isNotBlank()) { "Display title must not be blank" }
        require(releaseYear == null || releaseYear in 1800..9999) { "Invalid release year" }
    }
}
