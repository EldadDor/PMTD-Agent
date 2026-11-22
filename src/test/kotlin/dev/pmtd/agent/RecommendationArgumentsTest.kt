package dev.pmtd.agent

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RecommendationArgumentsTest {
    @Test
    fun `invalid recommendation arguments are rejected before application startup`() {
        val from = "--recommend-from=2030-01-01"
        val to = "--recommend-to=2030-01-31"
        val invalid = listOf(
            arrayOf(from),
            arrayOf(to),
            arrayOf(from, from, to),
            arrayOf(from, to, to),
            arrayOf(from, to, "--recommend-limit=0"),
            arrayOf(from, to, "--recommend-limit=11"),
            arrayOf(from, to, "--recommend-limit=abc"),
            arrayOf(from, "--recommend-to=2030-02-01"),
            arrayOf(from, "--recommend-to=2029-12-31"),
            arrayOf("--recommend-from=invalid", to),
            arrayOf(from, to, "--imdb-stats"),
            arrayOf(from, to, "--tmdb-enrich-limit=1"),
            arrayOf(from, to, "--recommend-imdb-ratings=ratings.tsv.gz"),
            arrayOf(from, to, "--recommend-imdb-date=2026-01-01"),
            arrayOf(from, to, "--recommend-imdb-ratings=", "--recommend-imdb-date=2026-01-01"),
            arrayOf(from, to, "--recommend-tmdb-context=true"),
        )
        for (args in invalid) {
            assertThatThrownBy { main(args) }.isInstanceOf(RuntimeException::class.java)
                .hasMessageNotContaining("Recommendation preview failed")
        }
    }
}
