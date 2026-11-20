package dev.pmtd.agent.imdb

import dev.pmtd.agent.catalog.*
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

data class ImportIssue(val row: Int, val reason: String)
data class ImportResult(val counts: ImportCounts, val skipped: Int, val issues: List<ImportIssue>, val alreadyImported: Boolean)
private data class ImportRow(val row: Int, val id: String, val rating: Int, val date: LocalDate?, val title: String, val kind: TitleKind, val year: Int?)

@Service
@Profile("postgres")
class ImdbImportService(
    private val catalog: CatalogRepository,
    private val history: ImdbHistoryRepository,
    private val jdbc: JdbcClient,
    private val transactions: TransactionTemplate,
) {
    fun import(path: Path): ImportResult {
        val digest = MessageDigest.getInstance("SHA-256")
        val rows = mutableListOf<ImportRow>()
        val issues = mutableListOf<ImportIssue>()
        val observedIds = mutableListOf<String>()
        var total = 0
        DigestInputStream(Files.newInputStream(path), digest).use { stream ->
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            val csv = CsvReader(InputStreamReader(stream, decoder))
            val headers = csv.readRecord() ?: throw CsvFormatException("Missing CSV header")
            val required = listOf("Const", "Your Rating", "Date Rated", "Title", "Title Type", "Year")
            require(headers.distinct().size == headers.size && required.all(headers::contains)) { "Missing or duplicate CSV headers" }
            val positions = headers.withIndex().associate { it.value to it.index }
            while (true) {
                val fields = csv.readRecord() ?: break
                total++
                val number = total + 1
                try {
                    val idPosition = positions.getValue("Const")
                    if (idPosition < fields.size) {
                        val candidate = fields[idPosition].trim()
                        if (candidate.matches(Regex("tt[0-9]+"))) observedIds.add(candidate)
                    }
                    require(fields.size == headers.size) { "Invalid column count" }
                    fun value(name: String) = fields[positions.getValue(name)].trim()
                    val id = value("Const")
                    require(id.matches(Regex("tt[0-9]+"))) { "Invalid IMDb ID" }
                    val rating = value("Your Rating").toIntOrNull()
                    require(rating != null && rating in 1..10) { "Invalid rating" }
                    val date = value("Date Rated").takeIf { it.isNotEmpty() }?.let {
                        try { LocalDate.parse(it) } catch (_: Exception) { throw IllegalArgumentException("Invalid rating date") }
                    }
                    val title = value("Title")
                    require(title.isNotBlank()) { "Invalid title" }
                    val yearText = value("Year")
                    val year = yearText.takeIf { it.isNotEmpty() }?.toIntOrNull()
                    require(yearText.isEmpty() || (year != null && year in 1800..9999)) { "Invalid year" }
                    val kind = when (value("Title Type").lowercase()) {
                        "movie" -> TitleKind.MOVIE
                        "tv series", "tv mini series" -> TitleKind.SERIES
                        "" -> TitleKind.UNKNOWN
                        else -> TitleKind.OTHER
                    }
                    rows.add(ImportRow(number, id, rating, date, title, kind, year))
                } catch (error: IllegalArgumentException) {
                    issues.add(ImportIssue(number, error.message ?: "Invalid row"))
                }
            }
        }
        val duplicates = observedIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        rows.filter { it.id in duplicates }.forEach { issues.add(ImportIssue(it.row, "Duplicate IMDb ID")) }
        val valid = rows.filterNot { it.id in duplicates }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return transactions.execute {
            jdbc.sql("SELECT pg_advisory_xact_lock(1347245124, 1)").query { _, _ -> true }.single()
            if (history.findBatch(hash) != null) {
                return@execute ImportResult(ImportCounts(0, 0, 0, 0, 0), total, emptyList(), true)
            }
            val batchId = UUID.randomUUID()
            val now = Instant.now()
            var inserted = 0
            var updated = 0
            var unchanged = 0
            var skipped = 0
            val writes = mutableListOf<ImportRow>()
            valid.forEach { row ->
                val existing = history.findRating(row.id)
                when {
                    existing == null -> { inserted++; writes.add(row) }
                    existing.rating == row.rating -> {
                        unchanged++
                        if (row.date != null && (existing.ratedOn == null || row.date > existing.ratedOn)) {
                            writes.add(row)
                        }
                    }
                    row.date != null && existing.ratedOn != null && row.date > existing.ratedOn -> {
                        updated++; writes.add(row)
                    }
                    else -> { skipped++; issues.add(ImportIssue(row.row, "Rating date conflict")) }
                }
            }
            // The existing schema groups skipped conflicts with invalid rows.
            val counts = ImportCounts(total, inserted, updated, unchanged, issues.size)
            check(history.tryCreateBatch(ImdbImportBatch(batchId, hash, now, counts)))
            writes.forEach { row ->
                val external = ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, row.id)
                val title = catalog.findByExternalId(external)
                if (title == null) {
                    val created = CatalogTitle(UUID.randomUUID(), row.title, row.kind, row.year, now, now)
                    catalog.save(created)
                    catalog.attachExternalId(created.id, external, "imdb_import", now)
                }
                history.saveRating(RatingWrite(row.id, row.rating, row.date, batchId, row.row, now))
            }
            ImportResult(counts, skipped, issues.sortedBy { it.row }, false)
        } ?: error("Import transaction returned no result")
    }
}
