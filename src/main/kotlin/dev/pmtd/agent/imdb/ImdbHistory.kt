package dev.pmtd.agent.imdb

import dev.pmtd.agent.catalog.ExternalIdNamespace
import dev.pmtd.agent.catalog.ExternalTitleId
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ImportCounts(
    val total: Int,
    val inserted: Int,
    val updated: Int,
    val unchanged: Int,
    val invalid: Int,
) {
    init {
        require(listOf(total, inserted, updated, unchanged, invalid).all { it >= 0 }) { "Negative import count" }
        require(total.toLong() == inserted.toLong() + updated + unchanged + invalid) { "Import counts do not balance" }
    }
}

data class ImdbImportBatch(val id: UUID, val contentSha256: String, val importedAt: Instant, val counts: ImportCounts) {
    init {
        require(contentSha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid import digest" }
    }
}

/** A storage command; export precedence/duplicate-row policy belongs to the future importer. */
data class RatingWrite(
    val imdbId: String,
    val rating: Int,
    val ratedOn: LocalDate?,
    val importId: UUID,
    val sourceRow: Int,
    val updatedAt: Instant,
) {
    init {
        ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, imdbId)
        require(rating in 1..10) { "IMDb rating must be between 1 and 10" }
        require(sourceRow > 0) { "Source row must be positive" }
    }
}

data class ImportedRating(
    val imdbId: String,
    val rating: Int,
    val ratedOn: LocalDate?,
    val firstImportId: UUID,
    val lastImportId: UUID,
    val lastSourceRow: Int,
    val updatedAt: Instant,
)
