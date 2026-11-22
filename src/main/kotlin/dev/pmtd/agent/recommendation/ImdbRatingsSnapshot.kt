package dev.pmtd.agent.recommendation

import java.io.IOException
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.zip.GZIPInputStream

internal data class AudienceRating(val score: BigDecimal, val votes: Long)

internal data class ImdbRatingsSnapshot(val date: LocalDate, val ratings: Map<String, AudienceRating>) {
    companion object {
        fun load(path: Path, date: LocalDate, ids: Set<String>): ImdbRatingsSnapshot {
            val ratings = mutableMapOf<String, AudienceRating>()
            try {
                Files.newInputStream(path).buffered().use { input ->
                    val stream = if (path.fileName.toString().endsWith(".gz")) GZIPInputStream(input) else input
                    stream.bufferedReader(Charsets.UTF_8).use { reader ->
                        require(reader.readLine() == "tconst\taverageRating\tnumVotes") {
                            "Unexpected IMDb ratings snapshot header"
                        }
                        reader.forEachLine { line ->
                            val id = line.substringBefore('\t')
                            if (id in ids) {
                                val fields = line.split('\t')
                                val score = fields.getOrNull(1)?.toBigDecimalOrNull()
                                val votes = fields.getOrNull(2)?.toLongOrNull()
                                require(fields.size == 3 && score != null && score in BigDecimal.ONE..BigDecimal.TEN &&
                                    votes != null && votes > 0) { "Invalid IMDb rating for a matching title" }
                                require(ratings.putIfAbsent(id, AudienceRating(score, votes)) == null) {
                                    "Duplicate IMDb rating for a matching title"
                                }
                            }
                        }
                    }
                }
            } catch (_: IOException) {
                throw IllegalArgumentException("Could not read the local IMDb ratings snapshot")
            }
            return ImdbRatingsSnapshot(date, ratings)
        }
    }
}

internal enum class AudienceDecision { SHORTLIST, REVIEW, SKIP }

internal fun classifyAudience(
    candidate: RecommendationCandidate,
    snapshot: ImdbRatingsSnapshot?,
    today: LocalDate,
): AudienceDecision {
    if (snapshot == null || snapshot.date !in today.minusDays(2)..today) return AudienceDecision.REVIEW
    val rating = snapshot.ratings[candidate.imdbId] ?: return AudienceDecision.REVIEW
    if (rating.score < BigDecimal("6.5")) return AudienceDecision.SKIP
    val needsFormatReview = candidate.genres.any { it == 99L || (candidate.media == "tv" && it == 10764L) }
    return if (rating.votes < 100 || needsFormatReview) AudienceDecision.REVIEW else AudienceDecision.SHORTLIST
}
