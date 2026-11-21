package dev.pmtd.agent.discovery

import dev.pmtd.agent.catalog.CatalogRepository
import dev.pmtd.agent.catalog.ExternalIdNamespace
import dev.pmtd.agent.catalog.ExternalTitleId
import java.time.LocalDate

internal enum class TmdbMatch(val label: String) {
    MATCHED("matched"),
    UNMATCHED("unmatched"),
    CONFLICT("conflict"),
    NOT_CHECKED("not checked (lookup limit)"),
}

internal class TmdbPreview(private val discovery: TmdbDiscovery, private val catalog: CatalogRepository) {
    fun render(from: LocalDate, to: LocalDate): String = buildString {
        appendLine("This product uses the TMDB API but is not endorsed or certified by TMDB.")
        appendLine("TMDB discovery preview | Original English query | $from to $to")
        for (media in TmdbMedia.entries) {
            val results = discovery.discover(media, from, to)
            appendLine("TMDB ${media.resource} candidates: ${results.candidates.size}")
            if (results.morePages) appendLine("Additional pages were left unread (two-page limit).")
            results.candidates.forEachIndexed { index, candidate ->
                val status = if (index < 10) match(candidate, discovery.imdbId(candidate)) else TmdbMatch.NOT_CHECKED
                val countries = candidate.originCountries.joinToString(", ").ifEmpty { "unknown" }
                val genres = candidate.genreIds.joinToString(", ").ifEmpty { "unknown" }
                appendLine(
                    "${candidate.id} | ${candidate.title} | ${media.dateLabel} ${candidate.date ?: "unknown"} | " +
                        "original language ${candidate.originalLanguage ?: "unknown"} | origin $countries | " +
                        "genre IDs $genres | IMDb ${status.label}",
                )
            }
        }
    }.trimEnd()

    internal fun match(candidate: TmdbCandidate, imdbId: String?): TmdbMatch {
        if (imdbId == null) return TmdbMatch.UNMATCHED
        val namespace = candidate.media.namespace
        val candidateId = candidate.id.toString()
        val tmdbTitle = catalog.findByExternalId(ExternalTitleId(namespace, candidateId))
        val imdbTitle = catalog.findByExternalId(ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, imdbId))

        if (tmdbTitle != null) {
            if (tmdbTitle.kind != candidate.media.kind || imdbTitle != null && tmdbTitle.id != imdbTitle.id) {
                return TmdbMatch.CONFLICT
            }
            val existingImdb = catalog.findExternalId(tmdbTitle.id, ExternalIdNamespace.IMDB_TITLE)
            if (existingImdb != null && existingImdb != imdbId) return TmdbMatch.CONFLICT
        }
        if (imdbTitle == null) return TmdbMatch.UNMATCHED
        if (imdbTitle.kind != candidate.media.kind) return TmdbMatch.CONFLICT
        val existingTmdb = catalog.findExternalId(imdbTitle.id, namespace)
        return if (existingTmdb != null && existingTmdb != candidateId) TmdbMatch.CONFLICT else TmdbMatch.MATCHED
    }
}
