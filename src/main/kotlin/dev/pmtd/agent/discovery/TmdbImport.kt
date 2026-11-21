package dev.pmtd.agent.discovery

import dev.pmtd.agent.catalog.CatalogRepository
import dev.pmtd.agent.catalog.ExternalIdNamespace
import dev.pmtd.agent.catalog.ExternalTitleId
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

internal data class TmdbObservation(val candidate: TmdbCandidate, val checked: Boolean, val imdbId: String?)
internal data class TmdbBatch(val media: TmdbMedia, val results: TmdbResults, val observations: List<TmdbObservation>)
internal data class TmdbImportResult(val candidates: Int, val conflicts: Int, val truncated: Boolean)
private data class StoredIdentity(val imdbId: String?, val titleId: UUID?, val status: String)

internal fun fetchTmdbBatch(discovery: TmdbDiscovery, media: TmdbMedia, from: LocalDate, to: LocalDate): TmdbBatch {
    val results = discovery.discover(media, from, to)
    val observations = results.candidates.mapIndexed { index, candidate ->
        val checked = index < 10
        TmdbObservation(candidate, checked, if (checked) discovery.imdbId(candidate) else null)
    }
    return TmdbBatch(media, results, observations)
}

@Service
@Profile("postgres")
internal class TmdbImport(private val jdbc: JdbcClient, private val transaction: TransactionTemplate,
                          private val catalog: CatalogRepository) {
    fun save(from: LocalDate, to: LocalDate, started: Instant, movie: TmdbBatch, tv: TmdbBatch): TmdbImportResult {
        validateTmdbWindow(from, to)
        require(movie.media == TmdbMedia.MOVIE && tv.media == TmdbMedia.TV)
        val observations = movie.observations + tv.observations
        require(observations.all { it.candidate.media == movie.media || it.candidate.media == tv.media })
        var conflicts = 0
        transaction.executeWithoutResult {
            jdbc.sql("SELECT pg_advisory_xact_lock(1347245126, 1)").query { _, _ -> true }.single()
            val now = Instant.now().atOffset(ZoneOffset.UTC)
            for (item in observations) {
                val c = item.candidate
                val existing = jdbc.sql("SELECT imdb_id, catalog_title_id, link_status FROM tmdb_candidate WHERE media_type = :media AND tmdb_id = :id FOR UPDATE")
                    .param("media", c.media.resource).param("id", c.id).query { row, _ ->
                        StoredIdentity(row.getString("imdb_id"), row.getObject("catalog_title_id", UUID::class.java), row.getString("link_status"))
                    }.optional().orElse(null)
                val identity = resolve(item, existing)
                if (identity.second) conflicts++
                val state = identity.first
                jdbc.sql("""
                    INSERT INTO tmdb_candidate (media_type, tmdb_id, title, primary_date, original_language,
                        genre_ids, origin_countries, imdb_id, catalog_title_id, link_status, first_seen_at, last_seen_at)
                    VALUES (:media, :id, :title, :date, :language, CAST(:genres AS bigint[]), CAST(:countries AS text[]),
                        :imdb, :titleId, :status, :now, :now)
                    ON CONFLICT (media_type, tmdb_id) DO UPDATE SET title = EXCLUDED.title,
                        primary_date = EXCLUDED.primary_date, original_language = EXCLUDED.original_language,
                        genre_ids = EXCLUDED.genre_ids, origin_countries = EXCLUDED.origin_countries,
                        imdb_id = EXCLUDED.imdb_id, catalog_title_id = EXCLUDED.catalog_title_id,
                        link_status = EXCLUDED.link_status, last_seen_at = EXCLUDED.last_seen_at
                """.trimIndent()).param("media", c.media.resource).param("id", c.id).param("title", c.title)
                    .param("date", c.date, Types.DATE).param("language", c.originalLanguage, Types.VARCHAR)
                    .param("genres", c.genreIds.joinToString(",", "{", "}"))
                    .param("countries", c.originCountries.joinToString(",", "{", "}"))
                    .param("imdb", state.imdbId, Types.VARCHAR).param("titleId", state.titleId, Types.OTHER)
                    .param("status", state.status).param("now", now).update()
            }
            jdbc.sql("""
                INSERT INTO tmdb_discovery_run (id, requested_from, requested_to, started_at, completed_at,
                    movie_pages, tv_pages, movie_truncated, tv_truncated)
                VALUES (:id, :from, :to, :started, :completed, :moviePages, :tvPages, :movieTruncated, :tvTruncated)
            """.trimIndent()).param("id", UUID.randomUUID()).param("from", from).param("to", to)
                .param("started", started.atOffset(ZoneOffset.UTC)).param("completed", now)
                .param("moviePages", movie.results.pagesFetched).param("tvPages", tv.results.pagesFetched)
                .param("movieTruncated", movie.results.morePages).param("tvTruncated", tv.results.morePages).update()
        }
        return TmdbImportResult(observations.size, conflicts, movie.results.morePages || tv.results.morePages)
    }

    private fun resolve(item: TmdbObservation, existing: StoredIdentity?): Pair<StoredIdentity, Boolean> {
        if (!item.checked) return (existing ?: StoredIdentity(null, null, "not_checked")) to false
        val imdbId = item.imdbId
        if (existing?.status == "linked" && imdbId != existing.imdbId) return existing to true
        if (imdbId == null) return StoredIdentity(null, null, "unmatched") to false
        val c = item.candidate
        val imdbTitle = catalog.findByExternalId(ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, imdbId))
        val tmdbTitle = catalog.findByExternalId(ExternalTitleId(c.media.namespace, c.id.toString()))
        val conflicting = tmdbTitle != null && (tmdbTitle.kind != c.media.kind ||
            imdbTitle != null && tmdbTitle.id != imdbTitle.id ||
            catalog.findExternalId(tmdbTitle.id, ExternalIdNamespace.IMDB_TITLE)?.let { it != imdbId } == true) ||
            imdbTitle != null && (imdbTitle.kind != c.media.kind ||
                catalog.findExternalId(imdbTitle.id, c.media.namespace)?.let { it != c.id.toString() } == true)
        if (conflicting || existing?.status == "linked" && imdbTitle?.id != existing.titleId) {
            return (if (existing?.status == "linked") existing else StoredIdentity(imdbId, null, "conflict")) to true
        }
        return if (imdbTitle == null) StoredIdentity(imdbId, null, "unmatched") to false
        else StoredIdentity(imdbId, imdbTitle.id, "linked") to false
    }
}
