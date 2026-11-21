package dev.pmtd.agent.recommendation

import java.time.LocalDate
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RecommendationScorerTest {
    private fun rated(id: Long, rating: Int, vararg genres: Long, media: String = "movie") =
        RatedEvidence(UUID(0, id), media, rating, genres.toList())

    private fun candidate(
        id: Long, genres: List<Long> = listOf(18), media: String = "movie", language: String? = "en",
        countries: List<String> = listOf("US"), date: LocalDate = LocalDate.parse("2030-01-01"),
    ) = RecommendationCandidate(media, id, "Synthetic $id", date, language, genres, countries)

    @Test
    fun `only distinct positive titles establish genre support and neutral ratings add nothing`() {
        val evidence = listOf(
            rated(1, 10, 18, 18), rated(1, 10, 18), rated(2, 8, 18), rated(3, 7, 18),
        )
        assertThat(RecommendationScorer.rank(evidence, listOf(candidate(1)), 5)).isEmpty()
        val supported = evidence + rated(4, 8, 18)
        val result = RecommendationScorer.rank(supported, listOf(candidate(1)), 5)
        assertThat(result.single().score).isEqualTo(1_000_000)
    }

    @Test
    fun `negative evidence reduces affinity and unsupported candidate genres count as zero`() {
        val evidence = listOf(rated(1, 10, 18), rated(2, 10, 18), rated(3, 10, 18), rated(4, 2, 18))
        val result = RecommendationScorer.rank(evidence, listOf(candidate(1, listOf(18, 18, 99))), 5)
        // (9 - 3) / (9 + 3) = 0.5; distinct candidate genres average to 0.25.
        assertThat(result).isEmpty()
        val eligible = RecommendationScorer.rank(evidence, listOf(candidate(2)), 5)
        assertThat(eligible.single().score).isEqualTo(500_000)
    }

    @Test
    fun `threshold is inclusive at 035 and floors fixed point division`() {
        val positive = (1L..9L).map { rated(it, 10, 18) }
        val negatives = listOf(rated(20, 1, 18), rated(21, 1, 18), rated(22, 1, 18), rated(23, 4, 18))
        val exact = RecommendationScorer.rank(positive + negatives, listOf(candidate(1)), 5)
        assertThat(exact.single().score).isEqualTo(350_000)
        val below = RecommendationScorer.rank(positive + negatives + rated(24, 4, 18), listOf(candidate(1)), 5)
        assertThat(below).isEmpty()
        val fraction = listOf(rated(31, 10, 18), rated(32, 10, 18), rated(33, 9, 18), rated(34, 2, 18))
        assertThat(RecommendationScorer.rank(fraction, listOf(candidate(2)), 5).single().score).isEqualTo(454_545)
    }

    @Test
    fun `movie evidence does not establish series support and empty genres are ineligible`() {
        val evidence = (1L..3L).map { rated(it, 10, 18) }
        assertThat(RecommendationScorer.rank(evidence, listOf(candidate(1, media = "tv"), candidate(2, genres = emptyList())), 5)).isEmpty()
        val tvEvidence = evidence + (4L..6L).map { rated(it, 8, 18, media = "tv") }
        assertThat(RecommendationScorer.rank(tvEvidence, listOf(candidate(1, media = "tv")), 5).single().score).isEqualTo(1_000_000)
    }

    @Test
    fun `equal scores order by language origin date media and id before limit`() {
        val evidence = (1L..3L).flatMap { listOf(rated(it, 8, 18), rated(it + 10, 8, 18, media = "tv")) }
        val candidates = listOf(
            candidate(9, language = "fr", date = LocalDate.parse("2030-01-03")),
            candidate(8, countries = listOf("FR"), date = LocalDate.parse("2030-01-03")),
            candidate(7, date = LocalDate.parse("2030-01-01")),
            candidate(3, media = "tv", date = LocalDate.parse("2030-01-02")),
            candidate(4, date = LocalDate.parse("2030-01-02")),
            candidate(2, date = LocalDate.parse("2030-01-02")),
        )
        assertThat(RecommendationScorer.rank(evidence, candidates.reversed(), 5).map { it.candidate.tmdbId })
            .containsExactly(2, 4, 3, 7, 8)
    }

    @Test
    fun `insufficient and entirely negative evidence produce no recommendations`() {
        assertThat(RecommendationScorer.rank(emptyList(), listOf(candidate(1)), 5)).isEmpty()
        val negative = (1L..3L).map { rated(it, 1, 18) }
        assertThat(RecommendationScorer.rank(negative, listOf(candidate(1)), 5)).isEmpty()
        assertThat(RecommendationScorer.rank(negative, emptyList(), 5)).isEmpty()
        assertThatThrownBy { RecommendationScorer.rank(negative, emptyList(), 0) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { RecommendationScorer.rank(negative, emptyList(), 11) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
