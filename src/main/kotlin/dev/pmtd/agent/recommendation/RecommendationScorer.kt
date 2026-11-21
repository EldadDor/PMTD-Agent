package dev.pmtd.agent.recommendation

import java.time.LocalDate
import java.util.UUID

internal data class RatedEvidence(val titleId: UUID, val media: String, val rating: Int, val genres: List<Long>)
internal data class RecommendationCandidate(
    val media: String, val tmdbId: Long, val title: String, val date: LocalDate,
    val language: String?, val genres: List<Long>, val countries: List<String>,
)
internal data class ScoredRecommendation(val candidate: RecommendationCandidate, val score: Int)

internal object RecommendationScorer {
    private const val SCALE = 1_000_000L
    private const val THRESHOLD = 350_000
    private val preferredCountries = setOf("US", "GB", "AU")

    fun rank(ratings: List<RatedEvidence>, candidates: List<RecommendationCandidate>, limit: Int): List<ScoredRecommendation> {
        require(limit in 1..10) { "Recommendation limit must be between 1 and 10" }
        data class Totals(var positive: Long = 0, var negative: Long = 0, var positiveTitles: Int = 0)
        val profiles = mutableMapOf<Pair<String, Long>, Totals>()
        for (rating in ratings.distinctBy { it.media to it.titleId }) {
            require(rating.media in setOf("movie", "tv") && rating.rating in 1..10) { "Invalid rating evidence" }
            for (genre in rating.genres.distinct()) {
                val total = profiles.getOrPut(rating.media to genre) { Totals() }
                when (rating.rating) {
                    in 8..10 -> { total.positive += rating.rating - 7; total.positiveTitles++ }
                    in 1..4 -> total.negative += 5 - rating.rating
                }
            }
        }
        val scores = profiles.mapValues { (_, total) ->
            if (total.positiveTitles < 3 || total.positive <= total.negative) 0L
            else (total.positive - total.negative) * SCALE / (total.positive + total.negative)
        }
        return candidates.mapNotNull { candidate ->
            val genres = candidate.genres.distinct()
            if (genres.isEmpty()) return@mapNotNull null
            val score = (genres.sumOf { scores[candidate.media to it] ?: 0L } / genres.size).toInt()
            if (score < THRESHOLD) null else ScoredRecommendation(candidate, score)
        }.sortedWith(
            compareByDescending<ScoredRecommendation> { it.score }
                .thenByDescending { it.candidate.language == "en" }
                .thenByDescending { item -> item.candidate.countries.any { it in preferredCountries } }
                .thenByDescending { it.candidate.date }
                .thenBy { it.candidate.media }
                .thenBy { it.candidate.tmdbId },
        ).take(limit)
    }
}
