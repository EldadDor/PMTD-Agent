package dev.pmtd.agent.notification

import dev.pmtd.agent.recommendation.ScoredRecommendation
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper

internal class LineDelivery(private val jdbc: JdbcClient, manager: PlatformTransactionManager) {
    private val tx = TransactionTemplate(manager)
    private val mapper = JsonMapper.builder().build()

    fun prepare(recipient: String, candidates: List<ScoredRecommendation>): Pair<UUID, String> {
        require(candidates.isNotEmpty() && candidates.size <= 5) { "Choose one to five recommendations" }
        val id = UUID.randomUUID()
        val items = candidates.map { UUID.randomUUID() to it.candidate }
        val bubbles = items.map { (itemId, item) ->
            val buttons = listOf("Interested" to "interested", "Not interested" to "not_interested", "Watched" to "watched")
            mapOf(
                "type" to "bubble",
                "body" to mapOf("type" to "box", "layout" to "vertical", "contents" to listOf(
                    mapOf("type" to "text", "text" to item.title, "wrap" to true, "weight" to "bold"),
                    mapOf("type" to "text", "text" to if (item.media == "movie") "Movie" else "TV series", "wrap" to true),
                )),
                "footer" to mapOf("type" to "box", "layout" to "vertical", "contents" to buttons.map { (label, action) ->
                    mapOf("type" to "button", "action" to mapOf(
                        "type" to "postback", "label" to label, "data" to "item=$itemId&action=$action",
                    ))
                } + mapOf("type" to "button", "action" to mapOf(
                    "type" to "uri", "label" to "Details",
                    "uri" to "https://www.themoviedb.org/${item.media}/${item.tmdbId}",
                ))),
            )
        }
        val payload = mapper.writeValueAsString(listOf(mapOf(
            "type" to "flex", "altText" to "PMTD-Agent recommendations",
            "contents" to mapOf("type" to "carousel", "contents" to bubbles),
        )))
        tx.executeWithoutResult {
            jdbc.sql("""
                INSERT INTO notification_delivery(id, retry_key, recipient_id, payload, status, created_at)
                VALUES (:id, :key, :recipient, :payload, 'prepared', :created)
            """.trimIndent()).param("id", id).param("key", UUID.randomUUID()).param("recipient", recipient)
                .param("payload", payload).param("created", Timestamp.from(Instant.now())).update()
            items.forEachIndexed { index, (itemId, item) ->
                jdbc.sql("""
                    INSERT INTO notification_item(id, delivery_id, media_type, tmdb_id, title, position)
                    VALUES (:id, :delivery, :media, :tmdb, :title, :position)
                """.trimIndent()).param("id", itemId).param("delivery", id).param("media", item.media)
                    .param("tmdb", item.tmdbId).param("title", item.title).param("position", index + 1).update()
            }
        }
        return id to payload
    }

    fun send(id: UUID, recipient: String, gateway: LineGateway): LinePushResult {
        val claim = tx.execute {
            jdbc.sql("""
                UPDATE notification_delivery SET status = 'sending', attempted_at = COALESCE(attempted_at, :now)
                WHERE id = :id AND recipient_id = :recipient AND (
                    status = 'prepared' OR
                    (status = 'uncertain' AND attempted_at > :retry_after)
                )
                RETURNING retry_key, payload
            """.trimIndent()).param("id", id).param("recipient", recipient)
                .param("now", Timestamp.from(Instant.now()))
                .param("retry_after", Timestamp.from(Instant.now().minusSeconds(23 * 60 * 60)))
                .query { row, _ -> row.getObject("retry_key", UUID::class.java) to row.getString("payload") }
                .optional().orElseThrow { IllegalStateException("Delivery is not available for sending") }
        } ?: error("Unable to claim delivery")
        val outcome = gateway.push(claim.second, claim.first)
        tx.executeWithoutResult {
            jdbc.sql("""
                UPDATE notification_delivery SET status = :status, accepted_at = :accepted
                WHERE id = :id AND status = 'sending'
            """.trimIndent()).param("id", id).param("status", outcome.name.lowercase())
                .param("accepted", if (outcome == LinePushResult.ACCEPTED) Timestamp.from(Instant.now()) else null)
                .update()
        }
        return outcome
    }
}
