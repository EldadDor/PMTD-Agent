package dev.pmtd.agent.persistence

import dev.pmtd.agent.MovieTvAgentApplication
import dev.pmtd.agent.catalog.CatalogIdentityConflictException
import dev.pmtd.agent.catalog.CatalogRepository
import dev.pmtd.agent.catalog.CatalogTitle
import dev.pmtd.agent.catalog.ExternalIdNamespace
import dev.pmtd.agent.catalog.ExternalTitleId
import dev.pmtd.agent.catalog.TitleKind
import dev.pmtd.agent.imdb.ImdbHistoryRepository
import dev.pmtd.agent.imdb.ImdbImportBatch
import dev.pmtd.agent.imdb.ImdbImportService
import dev.pmtd.agent.imdb.ImportCounts
import dev.pmtd.agent.imdb.RatingWrite
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/** Never run by `test`. Requires an authorized, existing, empty disposable schema. No cleanup/drop is performed. */
@Tag("postgres")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresPersistenceTest {
    @TempDir
    lateinit var fixtureDirectory: Path

    private lateinit var context: ConfigurableApplicationContext
    private lateinit var settings: Map<String, Any>
    private lateinit var schema: String
    private val now = Instant.parse("2026-01-02T03:04:05Z")

    private val catalog get() = context.getBean(CatalogRepository::class.java)
    private val history get() = context.getBean(ImdbHistoryRepository::class.java)
    private val importer get() = context.getBean(ImdbImportService::class.java)
    private val jdbc get() = context.getBean(JdbcClient::class.java)
    private val transactions get() = TransactionTemplate(context.getBean(PlatformTransactionManager::class.java))

    @BeforeAll
    fun openAuthorizedTarget() {
        val values = listOf("HOST", "PORT", "NAME", "USERNAME", "PASSWORD", "SCHEMA", "SSLMODE")
            .associateWith { suffix ->
                requireNotNull(System.getenv("PMTD_TEST_DB_$suffix")?.takeIf { it.isNotBlank() }) {
                    "Missing PMTD_TEST_DB_$suffix; integration tests require an authorized disposable target"
                }
            }
        schema = values.getValue("SCHEMA")
        require(schema.matches(Regex("pmtd_test_[a-z0-9_]{1,53}"))) { "Invalid disposable test schema name" }
        settings = values.mapKeys { "PMTD_DB_${it.key}" }
        val url = "jdbc:postgresql://${values.getValue("HOST")}:${values.getValue("PORT")}/${values.getValue("NAME")}"
        val properties = Properties().apply {
            setProperty("user", values.getValue("USERNAME"))
            setProperty("password", values.getValue("PASSWORD"))
            setProperty("sslmode", values.getValue("SSLMODE"))
            setProperty("connectTimeout", "5")
            setProperty("socketTimeout", "30")
            setProperty("options", "-c statement_timeout=15000 -c lock_timeout=5000")
        }
        DriverManager.getConnection(url, properties).use { connection ->
            connection.prepareStatement("SELECT count(*) FROM pg_namespace WHERE nspname = ?").use { statement ->
                statement.setString(1, schema)
                statement.executeQuery().use { rows ->
                    rows.next()
                    check(rows.getInt(1) == 1) { "Disposable test schema must already exist" }
                }
            }
            connection.prepareStatement(
                "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = ?",
            ).use { statement ->
                statement.setString(1, schema)
                statement.executeQuery().use { rows ->
                    rows.next()
                    check(rows.getInt(1) == 0) { "Disposable test schema must be empty; no cleanup is automatic" }
                }
            }
        }
        context = startContext()
    }

    @AfterAll
    fun closeContext() {
        if (::context.isInitialized) context.close()
    }

    @Test
    fun `migration is confined to target schema and is not repeated`() {
        assertThat(jdbc.sql("SELECT current_schema()").query(String::class.java).single()).isEqualTo(schema)
        val flyway = context.getBean(Flyway::class.java)
        assertThat(flyway.info().applied()).hasSize(1)
        assertThat(flyway.info().current().version.version).isEqualTo("1")
        assertThat(flyway.migrate().migrationsExecuted).isZero()
        flyway.validate()
        val tables = jdbc.sql("SELECT tablename FROM pg_tables WHERE schemaname = :schema")
            .param("schema", schema).query(String::class.java).list()
        assertThat(tables).containsExactlyInAnyOrder(
            "flyway_schema_history", "catalog_title", "title_external_id", "imdb_import_batch", "imported_rating",
        )
    }

    @Test
    fun `catalog upsert preserves original creation and missing values`() {
        val title = title()
        catalog.save(title)
        catalog.save(title.copy(displayTitle = "Updated fixture", createdAt = now.plusSeconds(1), updatedAt = now.plusSeconds(2)))
        assertThat(catalog.findById(title.id)).isEqualTo(title.copy(displayTitle = "Updated fixture", updatedAt = now.plusSeconds(2)))
    }

    @Test
    fun `trusted identities cannot be reassigned and namespaces stay separate`() {
        val first = title()
        val second = title()
        catalog.save(first)
        catalog.save(second)
        val movieId = ExternalTitleId(ExternalIdNamespace.TMDB_MOVIE, "12345")
        val seriesId = ExternalTitleId(ExternalIdNamespace.TMDB_TV, "12345")
        catalog.attachExternalId(first.id, movieId, "fixture", now)
        catalog.attachExternalId(first.id, movieId, "fixture refresh", now.plusSeconds(1))
        catalog.attachExternalId(second.id, seriesId, "fixture", now)
        assertThat(catalog.findByExternalId(movieId)).isEqualTo(first)
        assertThat(catalog.findByExternalId(seriesId)).isEqualTo(second)
        assertThatThrownBy { catalog.attachExternalId(second.id, movieId, "fixture", now) }
            .isInstanceOf(CatalogIdentityConflictException::class.java)
        assertThatThrownBy {
            catalog.attachExternalId(first.id, ExternalTitleId(ExternalIdNamespace.TMDB_MOVIE, "99999"), "fixture", now)
        }.isInstanceOf(CatalogIdentityConflictException::class.java)
        assertThat(catalog.findByExternalId(movieId)).isEqualTo(first)
    }

    @Test
    fun `rating upsert retains first provenance without duplicate rows`() {
        val imdbId = imdbId()
        val title = title()
        val first = batch()
        val second = batch()
        transactions.executeWithoutResult {
            catalog.save(title)
            catalog.attachExternalId(title.id, ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, imdbId), "fixture", now)
            assertThat(history.tryCreateBatch(first)).isTrue()
            history.saveRating(RatingWrite(imdbId, 7, null, first.id, 2, now))
        }
        transactions.executeWithoutResult {
            assertThat(history.tryCreateBatch(second)).isTrue()
            val write = RatingWrite(imdbId, 9, LocalDate.parse("2026-01-01"), second.id, 3, now.plusSeconds(1))
            history.saveRating(write)
            history.saveRating(write)
        }
        val rating = requireNotNull(history.findRating(imdbId))
        assertThat(rating.rating).isEqualTo(9)
        assertThat(rating.ratedOn).isEqualTo(LocalDate.parse("2026-01-01"))
        assertThat(rating.firstImportId).isEqualTo(first.id)
        assertThat(rating.lastImportId).isEqualTo(second.id)
        assertThat(rating.lastSourceRow).isEqualTo(3)
        assertThat(jdbc.sql("SELECT count(*) FROM imported_rating WHERE imdb_id = :id")
            .param("id", imdbId).query(Long::class.java).single()).isEqualTo(1)
    }

    @Test
    fun `database enforces rating bounds and trusted ID references`() {
        val title = title()
        val imdbId = imdbId()
        val batch = batch()
        catalog.save(title)
        catalog.attachExternalId(title.id, ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, imdbId), "fixture", now)
        history.tryCreateBatch(batch)
        history.saveRating(RatingWrite(imdbId, 7, null, batch.id, 2, now))
        listOf(0, 11).forEach { invalid ->
            assertThatThrownBy {
                jdbc.sql("UPDATE imported_rating SET rating = :rating WHERE imdb_id = :id")
                    .param("rating", invalid).param("id", imdbId).update()
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }
        assertThatThrownBy { history.saveRating(RatingWrite(imdbId(), 7, null, batch.id, 2, now)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThat(history.findRating(imdbId)?.rating).isEqualTo(7)
    }

    @Test
    fun `concurrent duplicate batches converge on one committed record`() {
        val batch = batch()
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val attempts = (1..2).map {
                executor.submit<Boolean> {
                    check(start.await(5, TimeUnit.SECONDS))
                    transactions.execute { history.tryCreateBatch(batch.copy(id = UUID.randomUUID())) }
                }
            }
            start.countDown()
            assertThat(attempts.map { it.get(15, TimeUnit.SECONDS) }).containsExactlyInAnyOrder(true, false)
        }
        assertThat(history.findBatch(batch.contentSha256)).isNotNull()
        assertThat(history.tryCreateBatch(batch)).isFalse()
    }

    @Test
    fun `failed transaction leaves no batch catalog ID or rating`() {
        val title = title()
        val imdbId = imdbId()
        val batch = batch()
        assertThatThrownBy {
            transactions.executeWithoutResult {
                history.tryCreateBatch(batch)
                catalog.save(title)
                catalog.attachExternalId(title.id, ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, imdbId), "fixture", now)
                history.saveRating(RatingWrite(imdbId, 7, null, batch.id, 2, now))
                jdbc.sql("UPDATE imported_rating SET rating = 0 WHERE imdb_id = :id").param("id", imdbId).update()
            }
        }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThat(history.findBatch(batch.contentSha256)).isNull()
        assertThat(catalog.findById(title.id)).isNull()
        assertThat(catalog.findByExternalId(ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, imdbId))).isNull()
        assertThat(history.findRating(imdbId)).isNull()
    }

    @Test
    fun `committed data survives context and connection pool restart`() {
        val title = title()
        catalog.save(title)
        context.close()
        context = startContext()
        assertThat(catalog.findById(title.id)).isEqualTo(title)
        assertThat(context.getBean(Flyway::class.java).info().applied()).hasSize(1)
    }

    @Test
    fun `import uses personal rating and named headers while preserving optional values and quoted title`() {
        val id = imdbId()
        val path = fixtureCsv(
            "\uFEFFIMDb Rating,Title,Const,Year,Date Rated,Title Type,Your Rating\r\n" +
                "9.9,\"Synthetic,\nQuoted \"\"Title\"\"\",$id,,,TV Series,3\r\n",
        )

        val result = importer.import(path)

        assertThat(result.counts).isEqualTo(ImportCounts(1, 1, 0, 0, 0))
        val rating = requireNotNull(history.findRating(id))
        assertThat(rating.rating).isEqualTo(3)
        assertThat(rating.ratedOn).isNull()
        val title = requireNotNull(catalog.findByExternalId(ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, id)))
        assertThat(title.displayTitle).isEqualTo("Synthetic,\nQuoted \"Title\"")
        assertThat(title.releaseYear).isNull()
        assertThat(title.kind).isEqualTo(TitleKind.SERIES)
    }

    @Test
    fun `invalid rows and all duplicate IDs are rejected including a valid twin of an invalid row`() {
        val accepted = imdbId()
        val duplicated = imdbId()
        val mixedDuplicate = imdbId()
        val invalidDate = imdbId()
        val invalidYear = imdbId()
        val path = fixtureCsv(
            "$importHeader\n" +
                "$accepted,7,2026-01-01,Synthetic,Movie,2000\n" +
                "$duplicated,5,2026-01-01,Duplicate One,Movie,2000\n" +
                "$duplicated,6,2026-01-01,Duplicate Two,Movie,2000\n" +
                "$mixedDuplicate,0,2026-01-01,Invalid Twin,Movie,2000\n" +
                "$mixedDuplicate,8,2026-01-01,Valid Twin,Movie,2000\n" +
                "$invalidDate,8,2026-02-30,Invalid Date,Movie,2000\n" +
                "$invalidYear,8,,Invalid Year,Movie,not-a-year\n",
        )

        val result = importer.import(path)

        assertThat(result.counts).isEqualTo(ImportCounts(7, 1, 0, 0, 6))
        assertThat(result.skipped).isZero()
        assertThat(result.issues.map { it.row }).containsExactly(3, 4, 5, 6, 7, 8)
        assertThat(history.findRating(accepted)).isNotNull()
        listOf(duplicated, mixedDuplicate, invalidDate, invalidYear).forEach { id ->
            assertThat(history.findRating(id)).isNull()
            assertThat(catalog.findByExternalId(ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, id))).isNull()
        }
    }

    @Test
    fun `identical export digest is a no-op retaining original rating and provenance`() {
        val id = imdbId()
        val path = ratingCsv(id, 7, "2026-01-01")
        importer.import(path)
        val original = history.findRating(id)

        val repeat = importer.import(path)

        assertThat(repeat.alreadyImported).isTrue()
        assertThat(repeat.counts).isEqualTo(ImportCounts(0, 0, 0, 0, 0))
        assertThat(repeat.skipped).isEqualTo(1)
        assertThat(repeat.issues).isEmpty()
        assertThat(history.findRating(id)).isEqualTo(original)
    }

    @Test
    fun `only a newer known rating date can replace an existing different rating`() {
        val id = imdbId()
        importer.import(ratingCsv(id, 7, "2026-01-02"))
        val original = requireNotNull(history.findRating(id))
        listOf("2026-01-01", "2026-01-02", "").forEach { date ->
            val conflict = importer.import(ratingCsv(id, 9, date))
            assertThat(conflict.skipped).isEqualTo(1)
            assertThat(conflict.issues.map { it.reason }).containsExactly("Rating date conflict")
            assertThat(history.findRating(id)).isEqualTo(original)
        }

        val newer = importer.import(ratingCsv(id, 8, "2026-01-03"))

        assertThat(newer.counts.updated).isEqualTo(1)
        val current = requireNotNull(history.findRating(id))
        assertThat(current.rating).isEqualTo(8)
        assertThat(current.ratedOn).isEqualTo(LocalDate.parse("2026-01-03"))
        assertThat(current.firstImportId).isEqualTo(original.firstImportId)
        assertThat(current.lastImportId).isNotEqualTo(original.lastImportId)
    }

    @Test
    fun `a newer date for an unchanged rating prevents a later intermediate-date overwrite`() {
        val id = imdbId()
        importer.import(ratingCsv(id, 7, "2026-01-01"))

        val unchanged = importer.import(ratingCsv(id, 7, "2026-01-03"))
        val staleChange = importer.import(ratingCsv(id, 9, "2026-01-02"))

        assertThat(unchanged.counts.unchanged).isEqualTo(1)
        assertThat(staleChange.skipped).isEqualTo(1)
        val current = requireNotNull(history.findRating(id))
        assertThat(current.rating).isEqualTo(7)
        assertThat(current.ratedOn).isEqualTo(LocalDate.parse("2026-01-03"))
    }

    @Test
    fun `concurrent service imports serialize and return one imported and one digest no-op`() {
        val id = imdbId()
        val path = ratingCsv(id, 7, "2026-01-01")
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val attempts = (1..2).map {
                executor.submit<dev.pmtd.agent.imdb.ImportResult> {
                    check(start.await(5, TimeUnit.SECONDS))
                    importer.import(path)
                }
            }
            start.countDown()
            val results = attempts.map { it.get(20, TimeUnit.SECONDS) }
            assertThat(results.map { it.alreadyImported }).containsExactlyInAnyOrder(false, true)
            assertThat(results.map { it.counts.inserted }).containsExactlyInAnyOrder(1, 0)
        }
        assertThat(jdbc.sql("SELECT count(*) FROM imported_rating WHERE imdb_id = :id")
            .param("id", id).query(Long::class.java).single()).isEqualTo(1)
    }

    private fun fixtureCsv(contents: String): Path = fixtureDirectory.resolve("${UUID.randomUUID()}.csv")
        .also { Files.writeString(it, contents) }

    private fun ratingCsv(id: String, rating: Int, date: String): Path =
        fixtureCsv("$importHeader\n$id,$rating,$date,Synthetic,Movie,2000\n")

    private val importHeader = "Const,Your Rating,Date Rated,Title,Title Type,Year"

    private fun startContext(): ConfigurableApplicationContext {
        // Do not let ambient SPRING_DATASOURCE_*, SPRING_FLYWAY_* or spring.config.*
        // redirect integration writes away from the explicitly authorized test target.
        val environment = StandardEnvironment().apply {
            propertySources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)
            propertySources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)
            propertySources.addFirst(MapPropertySource("authorized-postgres-test-target", settings))
        }
        return SpringApplicationBuilder(MovieTvAgentApplication::class.java)
            .environment(environment).web(WebApplicationType.NONE).profiles("postgres").run()
    }

    private fun title() = CatalogTitle(UUID.randomUUID(), "Synthetic title", TitleKind.UNKNOWN, null, now, now)

    private fun imdbId() = "tt" + UUID.randomUUID().toString().filter { it.isDigit() }.take(10).padEnd(10, '0')

    private fun batch(): ImdbImportBatch {
        val digest = MessageDigest.getInstance("SHA-256").digest(UUID.randomUUID().toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return ImdbImportBatch(UUID.randomUUID(), digest, now, ImportCounts(1, 1, 0, 0, 0))
    }
}
