package dev.pmtd.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RatingDistributionTest {
    @Test
    fun `empty history shows all rating buckets as zero`() {
        assertThat(formatRatingDistribution(emptyMap()).lines())
            .containsExactly("Rating distribution:", *(1..10).map { "$it: 0" }.toTypedArray())
    }

    @Test
    fun `distribution shows synthetic counts and missing buckets`() {
        val lines = formatRatingDistribution(mapOf(2 to 3L, 10 to 5L)).lines()
        assertThat(lines).contains("2: 3", "10: 5", "1: 0", "9: 0")
    }
}
