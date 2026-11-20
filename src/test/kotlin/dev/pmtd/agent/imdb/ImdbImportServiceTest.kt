package dev.pmtd.agent.imdb

import dev.pmtd.agent.catalog.CatalogRepository
import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Answers
import org.mockito.Mockito
import org.mockito.stubbing.Answer
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

class ImdbImportServiceTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `missing personal rating header cannot fall back to public aggregate`() {
        val fixture = Fixture()
        val path = text("Const,IMDb Rating,Date Rated,Title,Title Type,Year\ntt100,8,2026-01-01,Synthetic,Movie,2000")

        assertThatThrownBy { fixture.service.import(path) }.isInstanceOf(IllegalArgumentException::class.java)
        fixture.assertNoDatabaseWork()
    }

    @Test
    fun `duplicate headers are rejected before database work`() {
        val fixture = Fixture()
        val path = text("$header,Title\ntt100,8,2026-01-01,Synthetic,Movie,2000,Other")

        assertThatThrownBy { fixture.service.import(path) }.isInstanceOf(IllegalArgumentException::class.java)
        fixture.assertNoDatabaseWork()
    }

    @Test
    fun `malformed final record prevents writing an earlier valid record`() {
        val fixture = Fixture()
        val path = text("$header\ntt100,8,2026-01-01,Synthetic,Movie,2000\ntt101,7,,\"unfinished")

        assertThatThrownBy { fixture.service.import(path) }.isInstanceOf(CsvFormatException::class.java)
        fixture.assertNoDatabaseWork()
    }

    @Test
    fun `invalid UTF8 is rejected before any database work`() {
        val fixture = Fixture()
        val path = directory.resolve("malformed-encoding.csv")
        Files.write(path, "$header\n".toByteArray(Charsets.UTF_8) + byteArrayOf(0xC3.toByte(), 0x28))

        assertThatThrownBy { fixture.service.import(path) }.isInstanceOf(java.nio.charset.CharacterCodingException::class.java)
        fixture.assertNoDatabaseWork()
    }

    @Test
    fun `empty export is rejected before database work`() {
        val fixture = Fixture()

        assertThatThrownBy { fixture.service.import(text("")) }.isInstanceOf(CsvFormatException::class.java)
        fixture.assertNoDatabaseWork()
    }

    @Test
    fun `a persistence failure rolls back the enclosing import transaction`() {
        val fixture = Fixture(failRatingWrite = true)
        val path = text("$header\ntt100,8,2026-01-01,Synthetic,Movie,2000")

        assertThatThrownBy { fixture.service.import(path) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Synthetic storage failure")
        assertThat(fixture.transactions.started).isEqualTo(1)
        assertThat(fixture.transactions.committed).isZero()
        assertThat(fixture.transactions.rolledBack).isEqualTo(1)
    }

    private fun text(value: String): Path = directory.resolve("fixture.csv").also { Files.writeString(it, value) }

    private class Fixture(failRatingWrite: Boolean = false) {
        val catalog = Mockito.mock(CatalogRepository::class.java)
        val history = Mockito.mock(ImdbHistoryRepository::class.java, Answer { invocation ->
            when (invocation.method.name) {
                "tryCreateBatch" -> true
                "saveRating" -> {
                    if (failRatingWrite) throw IllegalStateException("Synthetic storage failure")
                    null
                }
                else -> Answers.RETURNS_DEFAULTS.answer(invocation)
            }
        })
        val jdbc = Mockito.mock(JdbcClient::class.java, Answers.RETURNS_DEEP_STUBS)
        val transactions = RecordingTransactions()
        val service = ImdbImportService(catalog, history, jdbc, TransactionTemplate(transactions))

        fun assertNoDatabaseWork() {
            Mockito.verifyNoInteractions(catalog, history, jdbc)
            assertThat(transactions.started).isZero()
        }
    }

    private class RecordingTransactions : PlatformTransactionManager {
        var started = 0
        var committed = 0
        var rolledBack = 0

        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus {
            started++
            return SimpleTransactionStatus()
        }

        override fun commit(status: TransactionStatus) { committed++ }
        override fun rollback(status: TransactionStatus) { rolledBack++ }
    }

    private companion object {
        const val header = "Const,Your Rating,Date Rated,Title,Title Type,Year"
    }
}
