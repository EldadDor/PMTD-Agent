package dev.pmtd.agent.discovery

import dev.pmtd.agent.catalog.CatalogRepository
import dev.pmtd.agent.catalog.ExternalIdNamespace
import dev.pmtd.agent.catalog.ExternalTitleId
import java.sql.Types
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

internal data class RatedTitle(val titleId: UUID, val imdbId: String, val media: TmdbMedia)

@Service
@Profile("postgres")
internal class RatedTitleEnrichment(private val jdbc: JdbcClient, private val tx: TransactionTemplate,
                                    private val catalog: CatalogRepository) {
    fun pending(limit: Int, retry: Boolean): List<RatedTitle> {
        require(limit in 1..20) { "Limit must be between 1 and 20" }
        return jdbc.sql("""
            SELECT c.id, e.external_id, c.kind FROM imported_rating r
            JOIN title_external_id e ON e.namespace = 'imdb_title' AND e.external_id = r.imdb_id
            JOIN catalog_title c ON c.id = e.title_id
            LEFT JOIN rated_title_enrichment_attempt a ON a.catalog_title_id = c.id
            LEFT JOIN rated_title_tmdb_metadata m ON m.catalog_title_id = c.id
            WHERE c.kind IN ('movie','series') AND m.catalog_title_id IS NULL
              AND ((:retry = false AND a.catalog_title_id IS NULL) OR (:retry = true AND a.outcome IN ('unmatched','conflict')))
            ORDER BY e.external_id LIMIT :limit
        """.trimIndent()).param("retry", retry).param("limit", limit).query { row, _ ->
            RatedTitle(row.getObject("id", UUID::class.java), row.getString("external_id"),
                if (row.getString("kind") == "movie") TmdbMedia.MOVIE else TmdbMedia.TV)
        }.list()
    }

    fun record(item: RatedTitle, lookup: RatedLookup, retry: Boolean): String {
        return checkNotNull(tx.execute {
            jdbc.sql("SELECT pg_advisory_xact_lock(1347245127, 1)").query { _, _ -> true }.single()
            val title = jdbc.sql("SELECT kind FROM catalog_title WHERE id=:id FOR UPDATE").param("id", item.titleId)
                .query(String::class.java).optional().orElse(null)
            val current = catalog.findByExternalId(ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, item.imdbId))
            val previous = jdbc.sql("SELECT outcome FROM rated_title_enrichment_attempt WHERE catalog_title_id=:id")
                .param("id", item.titleId).query(String::class.java).optional().orElse(null)
            if (previous == "matched" || previous != null && !retry) return@execute "skipped"
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val metadata = lookup as? RatedLookup.Matched
            val target = metadata?.let { catalog.findByExternalId(ExternalTitleId(item.media.namespace, it.tmdbId.toString())) }
            val claimed = metadata?.let {
                jdbc.sql("SELECT catalog_title_id FROM rated_title_tmdb_metadata WHERE media_type=:media AND tmdb_id=:id")
                    .param("media", item.media.resource).param("id", it.tmdbId).query(UUID::class.java).optional().orElse(null)
            }
            val discoveredOwner = metadata?.let {
                jdbc.sql("SELECT catalog_title_id FROM tmdb_candidate WHERE media_type=:media AND tmdb_id=:id AND catalog_title_id IS NOT NULL")
                    .param("media", item.media.resource).param("id", it.tmdbId).query(UUID::class.java).optional().orElse(null)
            }
            val existingId = catalog.findExternalId(item.titleId, item.media.namespace)
            val conflict = title != item.media.kind.databaseValue || current?.id != item.titleId ||
                lookup is RatedLookup.Conflict || target != null && target.id != item.titleId ||
                claimed != null && claimed != item.titleId || discoveredOwner != null && discoveredOwner != item.titleId ||
                metadata != null && existingId != null && existingId != metadata.tmdbId.toString()
            val outcome = when {
                conflict -> "conflict"
                metadata == null -> "unmatched"
                else -> "matched"
            }
            if (outcome == "matched") {
                val match = checkNotNull(metadata)
                jdbc.sql("""
                    INSERT INTO rated_title_tmdb_metadata (catalog_title_id,media_type,tmdb_id,genre_ids,original_language,origin_countries,observed_at)
                    VALUES (:title,:media,:id,CAST(:genres AS bigint[]),:language,CAST(:countries AS text[]),:now)
                """.trimIndent()).param("title", item.titleId).param("media", item.media.resource).param("id", match.tmdbId)
                    .param("genres", match.genres.joinToString(",", "{", "}"))
                    .param("language", match.language, Types.VARCHAR)
                    .param("countries", match.countries.joinToString(",", "{", "}"))
                    .param("now", now).update()
            }
            jdbc.sql("""
                INSERT INTO rated_title_enrichment_attempt (catalog_title_id,outcome,attempted_at) VALUES (:title,:outcome,:now)
                ON CONFLICT (catalog_title_id) DO UPDATE SET outcome=EXCLUDED.outcome, attempted_at=EXCLUDED.attempted_at
            """.trimIndent()).param("title", item.titleId).param("outcome", outcome).param("now", now).update()
            outcome
        })
    }
}
