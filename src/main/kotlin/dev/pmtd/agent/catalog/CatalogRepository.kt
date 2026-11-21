package dev.pmtd.agent.catalog

import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.context.annotation.Profile
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

class CatalogIdentityConflictException : RuntimeException("Conflicting trusted catalog identity")

@Repository
@Profile("postgres")
class CatalogRepository(private val jdbc: JdbcClient) {
    /** Updates only an explicitly identified title; never matches titles by name. */
    fun save(title: CatalogTitle) {
        jdbc.sql(
            """
            INSERT INTO catalog_title (id, display_title, kind, release_year, created_at, updated_at)
            VALUES (:id, :displayTitle, :kind, :releaseYear, :createdAt, :updatedAt)
            ON CONFLICT (id) DO UPDATE SET display_title = EXCLUDED.display_title,
                kind = EXCLUDED.kind, release_year = EXCLUDED.release_year, updated_at = EXCLUDED.updated_at
            """.trimIndent(),
        ).param("id", title.id)
            .param("displayTitle", title.displayTitle)
            .param("kind", title.kind.databaseValue)
            .param("releaseYear", title.releaseYear, Types.INTEGER)
            .param("createdAt", title.createdAt.atOffset(ZoneOffset.UTC))
            .param("updatedAt", title.updatedAt.atOffset(ZoneOffset.UTC))
            .update()
    }

    fun findById(id: UUID): CatalogTitle? = jdbc.sql("SELECT * FROM catalog_title WHERE id = :id")
        .param("id", id).query { row, _ -> mapTitle(row) }.optional().orElse(null)

    fun findByExternalId(externalId: ExternalTitleId): CatalogTitle? = jdbc.sql(
        """
        SELECT title.* FROM catalog_title title JOIN title_external_id external ON external.title_id = title.id
        WHERE external.namespace = :namespace AND external.external_id = :externalId
        """.trimIndent(),
    ).param("namespace", externalId.namespace.databaseValue).param("externalId", externalId.value)
        .query { row, _ -> mapTitle(row) }.optional().orElse(null)

    fun findExternalId(titleId: UUID, namespace: ExternalIdNamespace): String? = jdbc.sql(
        "SELECT external_id FROM title_external_id WHERE title_id = :id AND namespace = :namespace",
    ).param("id", titleId).param("namespace", namespace.databaseValue)
        .query(String::class.java).optional().orElse(null)

    /** A known source ID can refresh its observation, but cannot move to another title. */
    fun attachExternalId(titleId: UUID, externalId: ExternalTitleId, observedFrom: String, observedAt: Instant) {
        require(observedFrom.isNotBlank()) { "Observation provenance must not be blank" }
        val updated = try {
            jdbc.sql(
                """
                INSERT INTO title_external_id
                    (namespace, external_id, title_id, observed_from, first_seen_at, last_seen_at)
                VALUES (:namespace, :externalId, :titleId, :observedFrom, :observedAt, :observedAt)
                ON CONFLICT (namespace, external_id) DO UPDATE
                    SET last_seen_at = GREATEST(title_external_id.last_seen_at, EXCLUDED.last_seen_at)
                    WHERE title_external_id.title_id = EXCLUDED.title_id
                """.trimIndent(),
            ).param("namespace", externalId.namespace.databaseValue).param("externalId", externalId.value)
                .param("titleId", titleId).param("observedFrom", observedFrom)
                .param("observedAt", observedAt.atOffset(ZoneOffset.UTC)).update()
        } catch (_: DuplicateKeyException) {
            // Do not include private metadata or driver messages in the domain error.
            throw CatalogIdentityConflictException()
        }
        if (updated == 0) throw CatalogIdentityConflictException()
    }

    private fun mapTitle(row: ResultSet): CatalogTitle {
        val year = row.getInt("release_year").let { if (row.wasNull()) null else it }
        return CatalogTitle(
            row.getObject("id", UUID::class.java), row.getString("display_title"),
            TitleKind.fromDatabase(row.getString("kind")), year,
            row.getObject("created_at", OffsetDateTime::class.java).toInstant(),
            row.getObject("updated_at", OffsetDateTime::class.java).toInstant(),
        )
    }
}
