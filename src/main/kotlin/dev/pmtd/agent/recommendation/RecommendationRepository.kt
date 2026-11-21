package dev.pmtd.agent.recommendation

import dev.pmtd.agent.discovery.validateTmdbWindow
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@Service
@Profile("postgres")
internal class RecommendationRepository(private val jdbc: JdbcClient, transactionManager: PlatformTransactionManager) {
    private val snapshot = TransactionTemplate(transactionManager).apply {
        isReadOnly = true
        isolationLevel = TransactionDefinition.ISOLATION_REPEATABLE_READ
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    fun preview(from: LocalDate, to: LocalDate, limit: Int): List<ScoredRecommendation> {
        validateTmdbWindow(from, to)
        require(limit in 1..10) { "Recommendation limit must be between 1 and 10" }
        return checkNotNull(snapshot.execute {
            val history = jdbc.sql("""
                SELECT m.catalog_title_id, m.media_type, r.rating, m.genre_ids
                FROM rated_title_tmdb_metadata m
                JOIN catalog_title t ON t.id = m.catalog_title_id
                  AND t.kind = CASE m.media_type WHEN 'movie' THEN 'movie' ELSE 'series' END
                JOIN title_external_id i ON i.title_id = t.id AND i.namespace = 'imdb_title'
                JOIN imported_rating r ON r.imdb_id = i.external_id
            """.trimIndent()).query { row, _ ->
                RatedEvidence(row.getObject("catalog_title_id", UUID::class.java), row.getString("media_type"),
                    row.getInt("rating"), row.longArray("genre_ids"))
            }.list()
            val candidates = jdbc.sql("""
                SELECT c.media_type, c.tmdb_id, c.title, c.primary_date, c.original_language,
                       c.genre_ids, c.origin_countries
                FROM tmdb_candidate c
                WHERE c.link_status = 'unmatched' AND c.imdb_id ~ '^tt[0-9]+$'
                  AND c.primary_date BETWEEN :from AND :to
                  AND NOT EXISTS (SELECT 1 FROM imported_rating r WHERE r.imdb_id = c.imdb_id)
                  AND NOT EXISTS (
                    SELECT 1 FROM rated_title_tmdb_metadata m
                    JOIN title_external_id i ON i.title_id = m.catalog_title_id AND i.namespace = 'imdb_title'
                    JOIN imported_rating r ON r.imdb_id = i.external_id
                    WHERE m.media_type = c.media_type AND m.tmdb_id = c.tmdb_id
                  )
                  AND NOT EXISTS (
                    SELECT 1 FROM title_external_id tm
                    JOIN title_external_id i ON i.title_id = tm.title_id AND i.namespace = 'imdb_title'
                    JOIN imported_rating r ON r.imdb_id = i.external_id
                    WHERE tm.namespace = CASE c.media_type WHEN 'movie' THEN 'tmdb_movie' ELSE 'tmdb_tv' END
                      AND tm.external_id = c.tmdb_id::text
                  )
                  AND NOT EXISTS (
                    SELECT 1 FROM title_external_id i
                    JOIN catalog_title t ON t.id = i.title_id
                    LEFT JOIN title_external_id tm ON tm.title_id = i.title_id
                      AND tm.namespace = CASE c.media_type WHEN 'movie' THEN 'tmdb_movie' ELSE 'tmdb_tv' END
                    WHERE i.namespace = 'imdb_title' AND i.external_id = c.imdb_id
                      AND (t.kind <> CASE c.media_type WHEN 'movie' THEN 'movie' ELSE 'series' END
                           OR (tm.external_id IS NOT NULL AND tm.external_id <> c.tmdb_id::text))
                  )
                  AND NOT EXISTS (
                    SELECT 1 FROM title_external_id tm
                    JOIN catalog_title t ON t.id = tm.title_id
                    LEFT JOIN title_external_id i ON i.title_id = tm.title_id AND i.namespace = 'imdb_title'
                    WHERE tm.namespace = CASE c.media_type WHEN 'movie' THEN 'tmdb_movie' ELSE 'tmdb_tv' END
                      AND tm.external_id = c.tmdb_id::text
                      AND (t.kind <> CASE c.media_type WHEN 'movie' THEN 'movie' ELSE 'series' END
                           OR (i.external_id IS NOT NULL AND i.external_id <> c.imdb_id))
                  )
            """.trimIndent()).param("from", from).param("to", to).query { row, _ ->
                RecommendationCandidate(row.getString("media_type"), row.getLong("tmdb_id"),
                    row.getString("title"), row.getObject("primary_date", LocalDate::class.java),
                    row.getString("original_language"), row.longArray("genre_ids"), row.stringArray("origin_countries"))
            }.list()
            RecommendationScorer.rank(history, candidates, limit)
        })
    }
}

private fun ResultSet.longArray(column: String): List<Long> =
    (getArray(column).array as Array<*>).map { (it as Number).toLong() }

private fun ResultSet.stringArray(column: String): List<String> =
    (getArray(column).array as Array<*>).map { it as String }
