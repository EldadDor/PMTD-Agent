package dev.pmtd.agent.notification

import java.sql.Timestamp
import java.time.Instant
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

internal class LineFeedbackRepository(jdbc: JdbcClient, transactionManager: PlatformTransactionManager) : LineFeedbackHandler {
    private val jdbc = jdbc
    private val transaction = TransactionTemplate(transactionManager)

    override fun accept(event: LinePostbackEvent) {
        transaction.executeWithoutResult {
            val inserted = jdbc.sql("""
                INSERT INTO line_webhook_event(event_id, received_at, outcome)
                VALUES (:event, :received, 'ignored') ON CONFLICT DO NOTHING
            """.trimIndent()).param("event", event.id).param("received", Timestamp.from(Instant.now())).update()
            if (inserted == 0) return@executeWithoutResult
            val occurred = Timestamp.from(Instant.ofEpochMilli(event.timestamp))
            val saved = jdbc.sql("""
                INSERT INTO feedback_event(event_id, item_id, action, occurred_at)
                SELECT :event, i.id, :action, :occurred FROM notification_item i
                JOIN notification_delivery d ON d.id = i.delivery_id
                WHERE i.id = :item AND d.status IN ('sending', 'accepted', 'uncertain')
            """.trimIndent()).param("event", event.id).param("item", event.itemId)
                .param("action", event.action).param("occurred", occurred).update()
            if (saved == 0) return@executeWithoutResult
            val feedbackViewExists = jdbc.sql("SELECT to_regclass('user_title_feedback') IS NOT NULL")
                .query(Boolean::class.java).single()
            if (!feedbackViewExists) {
                jdbc.sql("""
                    INSERT INTO user_title_state(media_type, tmdb_id, action, event_id, occurred_at)
                    SELECT media_type, tmdb_id, :action, :event, :occurred FROM notification_item WHERE id = :item
                    ON CONFLICT (media_type, tmdb_id) DO UPDATE
                    SET action = EXCLUDED.action, event_id = EXCLUDED.event_id, occurred_at = EXCLUDED.occurred_at
                    WHERE (user_title_state.occurred_at, user_title_state.event_id) <
                          (EXCLUDED.occurred_at, EXCLUDED.event_id)
                """.trimIndent()).param("event", event.id).param("item", event.itemId)
                    .param("action", event.action).param("occurred", occurred).update()
            }
            jdbc.sql("UPDATE line_webhook_event SET outcome = 'applied' WHERE event_id = :event")
                .param("event", event.id).update()
        }
    }
}
