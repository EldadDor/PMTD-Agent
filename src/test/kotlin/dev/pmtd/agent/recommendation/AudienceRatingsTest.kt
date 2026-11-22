package dev.pmtd.agent.recommendation

import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.zip.GZIPOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AudienceRatingsTest {
    @TempDir
    lateinit var directory: Path

    private val today = LocalDate.of(2026, 11, 22)
    private val id = "tt1000001"
    private val header = "tconst\taverageRating\tnumVotes\n"

    @Test
    fun `loads exact IDs from compressed and plain snapshots`() {
        val data = header + "tt10000010\t9.1\t1234\n$id\t6.5\t100\n"
        val plain = Files.writeString(directory.resolve("title.ratings.tsv"), data)
        val compressed = directory.resolve("title.ratings.tsv.gz")
        GZIPOutputStream(Files.newOutputStream(compressed)).use { it.write(data.toByteArray(Charsets.UTF_8)) }

        for (path in listOf(plain, compressed)) {
            val snapshot = ImdbRatingsSnapshot.load(path, today, setOf(id, "tt1000002"))
            assertThat(snapshot.ratings.entries).hasSize(1)
            assertThat(snapshot.ratings.getValue(id)).isEqualTo(AudienceRating(BigDecimal("6.5"), 100))
        }
    }

    @Test
    fun `rejects an invalid header and malformed or duplicate matching rows`() {
        val invalid = listOf(
            "wrong\tcolumns\there\n$id\t6.5\t100\n",
            header + "$id\tbad\t100\n",
            header + "$id\t10.1\t100\n",
            header + "$id\t6.5\t-1\n",
            header + "$id\t6.5\t100\n$id\t6.5\t100\n",
        )
        for (data in invalid) {
            val file = Files.writeString(directory.resolve("invalid.tsv"), data)
            assertThatThrownBy { ImdbRatingsSnapshot.load(file, today, setOf(id)) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `distinguishes low scores from low confidence and accepts the boundaries`() {
        val title = candidate()
        assertThat(classifyAudience(title, snapshot("6.4", 1), today)).isEqualTo(AudienceDecision.SKIP)
        assertThat(classifyAudience(title, snapshot("8.0", 99), today)).isEqualTo(AudienceDecision.REVIEW)
        assertThat(classifyAudience(title, snapshot("6.5", 100), today)).isEqualTo(AudienceDecision.SHORTLIST)
        assertThat(classifyAudience(title, snapshot("6.5", 1000), today)).isEqualTo(AudienceDecision.SHORTLIST)
    }

    @Test
    fun `routes missing stale and format-sensitive evidence to review`() {
        val title = candidate()
        assertThat(classifyAudience(title, null, today)).isEqualTo(AudienceDecision.REVIEW)
        assertThat(classifyAudience(title.copy(imdbId = "tt1000002"), snapshot("8.0", 1000), today))
            .isEqualTo(AudienceDecision.REVIEW)
        assertThat(classifyAudience(title, snapshot("8.0", 1000, today.minusDays(2)), today))
            .isEqualTo(AudienceDecision.SHORTLIST)
        assertThat(classifyAudience(title, snapshot("8.0", 1000, today.minusDays(3)), today))
            .isEqualTo(AudienceDecision.REVIEW)
        assertThat(classifyAudience(title, snapshot("8.0", 1000, today.plusDays(1)), today))
            .isEqualTo(AudienceDecision.REVIEW)
        for (genre in listOf(99L, 10764L)) {
            assertThat(classifyAudience(title.copy(genres = listOf(18, genre)), snapshot("8.0", 1000), today))
                .isEqualTo(AudienceDecision.REVIEW)
        }
    }

    private fun candidate() = RecommendationCandidate(
        "tv", 7, "Synthetic series", today, "en", listOf(18), listOf("US"), imdbId = id,
    )

    private fun snapshot(score: String, votes: Long, date: LocalDate = today): ImdbRatingsSnapshot {
        val file = Files.writeString(directory.resolve("title.ratings.tsv"), header + "$id\t$score\t$votes\n")
        return ImdbRatingsSnapshot.load(file, date, setOf(id))
    }
}
