package dev.pmtd.agent.discovery

import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
@Profile("postgres")
class TvmazeScheduleImport(private val jdbc: JdbcClient, private val transaction: TransactionTemplate) {
    fun save(country: String, date: LocalDate, episodes: List<Episode>): Int {
        require(country.matches(Regex("[A-Z]{2}"))) { "Invalid country" }
        val validated = episodes.map { episode ->
            require(episode.episodeId > 0 && episode.showId > 0) { "Invalid source ID" }
            episode to episode.airDate?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
        }
        require(episodes.map { it.episodeId }.toSet().size == episodes.size) { "Duplicate episode ID" }
        transaction.executeWithoutResult {
            jdbc.sql("SELECT pg_advisory_xact_lock(1347245125, hashtext(:country))")
                .param("country", country).query { _, _ -> true }.single()
            val now = Instant.now().atOffset(ZoneOffset.UTC)
            for ((episode, airDate) in validated) {
                jdbc.sql("""
                    INSERT INTO tvmaze_schedule_episode
                        (country, episode_id, show_id, show_name, episode_name, air_date, source_url, first_seen_at, last_seen_at)
                    VALUES (:country, :episodeId, :showId, :showName, :episodeName, :airDate, :sourceUrl, :now, :now)
                    ON CONFLICT (country, episode_id) DO UPDATE SET
                        show_id = EXCLUDED.show_id, show_name = EXCLUDED.show_name,
                        episode_name = EXCLUDED.episode_name, air_date = EXCLUDED.air_date,
                        source_url = EXCLUDED.source_url, last_seen_at = EXCLUDED.last_seen_at
                """.trimIndent()).param("country", country).param("episodeId", episode.episodeId)
                    .param("showId", episode.showId).param("showName", episode.show, Types.VARCHAR)
                    .param("episodeName", episode.episode, Types.VARCHAR).param("airDate", airDate, Types.DATE)
                    .param("sourceUrl", episode.sourceUrl, Types.VARCHAR).param("now", now).update()
            }
            jdbc.sql("""
                INSERT INTO tvmaze_schedule_checkpoint (country, schedule_date, last_success_at)
                VALUES (:country, :date, :now)
                ON CONFLICT (country, schedule_date) DO UPDATE SET last_success_at = EXCLUDED.last_success_at
            """.trimIndent()).param("country", country).param("date", date).param("now", now).update()
        }
        return validated.size
    }
}
