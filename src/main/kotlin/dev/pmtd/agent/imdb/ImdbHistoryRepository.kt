package dev.pmtd.agent.imdb

import java.sql.Types
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/** Primitives participate in the caller's transaction; they do not implement a CSV import. */
@Repository
@Profile("postgres")
class ImdbHistoryRepository(private val jdbc: JdbcClient) {
    /** The caller must skip all further writes when a digest was already committed. */
    fun tryCreateBatch(batch: ImdbImportBatch): Boolean = jdbc.sql(
        """
        INSERT INTO imdb_import_batch
            (id, content_sha256, imported_at, total_rows, inserted_rows, updated_rows, unchanged_rows, invalid_rows)
        VALUES (:id, :digest, :importedAt, :total, :inserted, :updated, :unchanged, :invalid)
        ON CONFLICT (content_sha256) DO NOTHING
        """.trimIndent(),
    ).param("id", batch.id).param("digest", batch.contentSha256)
        .param("importedAt", batch.importedAt.atOffset(ZoneOffset.UTC))
        .param("total", batch.counts.total).param("inserted", batch.counts.inserted)
        .param("updated", batch.counts.updated).param("unchanged", batch.counts.unchanged)
        .param("invalid", batch.counts.invalid).update() == 1

    fun findBatch(contentSha256: String): ImdbImportBatch? = jdbc.sql(
        "SELECT * FROM imdb_import_batch WHERE content_sha256 = :digest",
    ).param("digest", contentSha256).query { row, _ ->
        ImdbImportBatch(
            row.getObject("id", UUID::class.java), row.getString("content_sha256"),
            row.getObject("imported_at", OffsetDateTime::class.java).toInstant(),
            ImportCounts(
                row.getInt("total_rows"), row.getInt("inserted_rows"), row.getInt("updated_rows"),
                row.getInt("unchanged_rows"), row.getInt("invalid_rows"),
            ),
        )
    }.optional().orElse(null)

    /** Apply an explicit rating decision, retaining the original import provenance. */
    fun saveRating(write: RatingWrite) {
        jdbc.sql(
            """
            INSERT INTO imported_rating
                (imdb_id, rating, rated_on, first_import_id, last_import_id, last_source_row, updated_at)
            VALUES (:imdbId, :rating, :ratedOn, :importId, :importId, :sourceRow, :updatedAt)
            ON CONFLICT (imdb_id) DO UPDATE SET rating = EXCLUDED.rating, rated_on = EXCLUDED.rated_on,
                last_import_id = EXCLUDED.last_import_id, last_source_row = EXCLUDED.last_source_row,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
        ).param("imdbId", write.imdbId).param("rating", write.rating)
            .param("ratedOn", write.ratedOn, Types.DATE).param("importId", write.importId)
            .param("sourceRow", write.sourceRow).param("updatedAt", write.updatedAt.atOffset(ZoneOffset.UTC))
            .update()
    }

    fun findRating(imdbId: String): ImportedRating? = jdbc.sql("SELECT * FROM imported_rating WHERE imdb_id = :imdbId")
        .param("imdbId", imdbId).query { row, _ ->
            ImportedRating(
                row.getString("imdb_id"), row.getInt("rating"), row.getObject("rated_on", LocalDate::class.java),
                row.getObject("first_import_id", UUID::class.java), row.getObject("last_import_id", UUID::class.java),
                row.getInt("last_source_row"), row.getObject("updated_at", OffsetDateTime::class.java).toInstant(),
            )
        }.optional().orElse(null)
}
