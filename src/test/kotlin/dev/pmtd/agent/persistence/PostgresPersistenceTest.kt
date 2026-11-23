package dev.pmtd.agent.persistence

import dev.pmtd.agent.MovieTvAgentApplication
import dev.pmtd.agent.discovery.RatedTitle
import dev.pmtd.agent.discovery.RatedLookup
import dev.pmtd.agent.discovery.RatedTitleEnrichment
import dev.pmtd.agent.discovery.TmdbBatch
import dev.pmtd.agent.discovery.TmdbCandidate
import dev.pmtd.agent.discovery.TmdbImport
import dev.pmtd.agent.discovery.TmdbMedia
import dev.pmtd.agent.discovery.TmdbObservation
import dev.pmtd.agent.discovery.TmdbResults
import dev.pmtd.agent.discovery.Episode
import dev.pmtd.agent.discovery.TvmazeScheduleImport
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
import dev.pmtd.agent.notification.LineFeedbackRepository
import dev.pmtd.agent.notification.LinePostbackEvent
import dev.pmtd.agent.recommendation.RecommendationRepository
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
        assertThat(flyway.info().applied()).hasSize(5)
        assertThat(flyway.info().current().version.version).isEqualTo("5")
        assertThat(flyway.migrate().migrationsExecuted).isZero()
        flyway.validate()
        val tables = jdbc.sql("SELECT tablename FROM pg_tables WHERE schemaname = :schema")
            .param("schema", schema).query(String::class.java).list()
        assertThat(tables).containsExactlyInAnyOrder(
            "flyway_schema_history", "catalog_title", "title_external_id", "imdb_import_batch", "imported_rating",
            "tvmaze_schedule_episode", "tvmaze_schedule_checkpoint", "tmdb_candidate", "tmdb_discovery_run", "rated_title_tmdb_metadata", "rated_title_enrichment_attempt",
            "notification_delivery", "notification_item", "line_webhook_event", "feedback_event", "user_title_state",
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
        assertThat(context.getBean(Flyway::class.java).info().applied()).hasSize(5)
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

    @Test
    fun `TVmaze import is repeatable across countries and preserves checkpoint on invalid input`() {
        val service = context.getBean(TvmazeScheduleImport::class.java)
        val day = LocalDate.parse("2026-11-20")
        val episode = Episode(9912345, 9923456, "Synthetic", "Pilot", "2026-11-20", null)
        assertThat(service.save("US", day, listOf(episode))).isEqualTo(1)
        assertThat(service.save("US", day, listOf(episode.copy(airDate = "2026-11-21")))).isEqualTo(1)
        service.save("GB", day, listOf(episode))
        assertThat(jdbc.sql("SELECT count(*) FROM tvmaze_schedule_episode WHERE episode_id = 9912345")
            .query(Long::class.java).single()).isEqualTo(2)
        assertThat(jdbc.sql("SELECT air_date FROM tvmaze_schedule_episode WHERE country = 'US' AND episode_id = 9912345")
            .query(LocalDate::class.java).single()).isEqualTo(LocalDate.parse("2026-11-21"))
        val later = day.plusDays(2)
        assertThatThrownBy { service.save("US", later, listOf(episode.copy(airDate = "invalid"))) }
            .isInstanceOf(java.time.format.DateTimeParseException::class.java)
        assertThat(jdbc.sql("SELECT count(*) FROM tvmaze_schedule_checkpoint WHERE country = 'US' AND schedule_date = :date")
            .param("date", later).query(Long::class.java).single()).isZero()
    }

    @Test
    fun `TVmaze observations and checkpoint roll back together`() {
        val service = context.getBean(TvmazeScheduleImport::class.java)
        val day = LocalDate.parse("2030-01-01")
        val episode = Episode(9912350, 9923456, "Synthetic", null, null, null)
        assertThatThrownBy {
            transactions.executeWithoutResult {
                service.save("FR", day, listOf(episode))
                throw IllegalStateException("synthetic failure")
            }
        }.isInstanceOf(IllegalStateException::class.java)
        assertThat(jdbc.sql("SELECT count(*) FROM tvmaze_schedule_episode WHERE country = 'FR' AND episode_id = 9912350")
            .query(Long::class.java).single()).isZero()
        assertThat(jdbc.sql("SELECT count(*) FROM tvmaze_schedule_checkpoint WHERE country = 'FR' AND schedule_date = :date")
            .param("date", day).query(Long::class.java).single()).isZero()
    }

    @Test
    fun `concurrent TVmaze observations converge on one row`() {
        val service = context.getBean(TvmazeScheduleImport::class.java)
        val episode = Episode(9912351, 9923456, "Synthetic", null, null, null)
        val day = LocalDate.parse("2030-01-02")
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val jobs = (1..2).map {
                executor.submit<Int> {
                    check(start.await(5, TimeUnit.SECONDS))
                    service.save("CA", day, listOf(episode))
                }
            }
            start.countDown()
            assertThat(jobs.map { it.get(15, TimeUnit.SECONDS) }).containsExactly(1, 1)
        }
        assertThat(jdbc.sql("SELECT count(*) FROM tvmaze_schedule_episode WHERE country = 'CA' AND episode_id = 9912351")
            .query(Long::class.java).single()).isEqualTo(1)
    }

    @Test
    fun `TMDB import preserves verified identity on conflicting and unchecked later observations`() {
        val catalogTitle = title().copy(kind = TitleKind.MOVIE)
        val imdb = imdbId()
        catalog.save(catalogTitle)
        catalog.attachExternalId(catalogTitle.id, ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, imdb), "fixture", now)
        val item = TmdbCandidate(TmdbMedia.MOVIE, 555001, "Fixture", LocalDate.parse("2026-11-20"), "en", listOf(18), listOf("US"))
        fun batch(checked: Boolean, id: String?): TmdbBatch {
            val result = TmdbResults(listOf(item), true, 2)
            return TmdbBatch(TmdbMedia.MOVIE, result, listOf(TmdbObservation(item, checked, id)))
        }
        val empty = TmdbBatch(TmdbMedia.TV, TmdbResults(emptyList(), false, 1), emptyList())
        val service = context.getBean(TmdbImport::class.java)
        val day = LocalDate.parse("2026-11-20")
        assertThat(service.save(day, day, now, batch(true, imdb), empty).truncated).isTrue()
        assertThat(service.save(day, day, now, batch(true, "tt9999999999"), empty).conflicts).isEqualTo(1)
        service.save(day, day, now, batch(false, null), empty)
        val row = jdbc.sql("SELECT imdb_id, catalog_title_id, link_status FROM tmdb_candidate WHERE media_type='movie' AND tmdb_id=555001")
            .query { rs, _ -> Triple(rs.getString(1), rs.getObject(2, UUID::class.java), rs.getString(3)) }.single()
        assertThat(row).isEqualTo(Triple(imdb, catalogTitle.id, "linked"))
    }

    @Test
    fun `TMDB observations and run evidence roll back together`() {
        val item = TmdbCandidate(TmdbMedia.MOVIE, 555002, "Fixture", null, "en", emptyList(), emptyList())
        val movie = TmdbBatch(TmdbMedia.MOVIE, TmdbResults(listOf(item), false, 1), listOf(TmdbObservation(item, true, null)))
        val tv = TmdbBatch(TmdbMedia.TV, TmdbResults(emptyList(), false, 1), emptyList())
        val day = LocalDate.parse("2031-01-01")
        assertThatThrownBy { transactions.executeWithoutResult {
            context.getBean(TmdbImport::class.java).save(day, day, now, movie, tv)
            throw IllegalStateException("fixture rollback")
        } }.isInstanceOf(IllegalStateException::class.java)
        assertThat(jdbc.sql("SELECT count(*) FROM tmdb_candidate WHERE media_type='movie' AND tmdb_id=555002").query(Long::class.java).single()).isZero()
        assertThat(jdbc.sql("SELECT count(*) FROM tmdb_discovery_run WHERE requested_from = :day").param("day", day).query(Long::class.java).single()).isZero()
    }

    @Test
    fun `concurrent TMDB imports retain one candidate and record both runs`() {
        val service = context.getBean(TmdbImport::class.java)
        val item = TmdbCandidate(TmdbMedia.MOVIE, 555003, "Concurrent fixture", null, "en", emptyList(), emptyList())
        val movie = TmdbBatch(TmdbMedia.MOVIE, TmdbResults(listOf(item), false, 1), listOf(TmdbObservation(item, true, null)))
        val tv = TmdbBatch(TmdbMedia.TV, TmdbResults(emptyList(), false, 1), emptyList())
        val day = LocalDate.parse("2031-01-02")
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val jobs = (1..2).map {
                executor.submit<Int> {
                    check(start.await(5, TimeUnit.SECONDS))
                    service.save(day, day, now, movie, tv).candidates
                }
            }
            start.countDown()
            assertThat(jobs.map { it.get(15, TimeUnit.SECONDS) }).containsExactly(1, 1)
        }
        assertThat(jdbc.sql("SELECT count(*) FROM tmdb_candidate WHERE media_type='movie' AND tmdb_id=555003").query(Long::class.java).single()).isEqualTo(1)
        assertThat(jdbc.sql("SELECT count(*) FROM tmdb_discovery_run WHERE requested_from=:day").param("day", day).query(Long::class.java).single()).isEqualTo(2)
    }

    @Test
    fun `rated title enrichment records safe matches and skips duplicates`() {
        val service = context.getBean(RatedTitleEnrichment::class.java)
        val title = title().copy(kind = TitleKind.MOVIE)
        val id = imdbId()
        catalog.save(title)
        catalog.attachExternalId(title.id, ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, id), "fixture", now)
        val batch = batch()
        history.tryCreateBatch(batch)
        history.saveRating(RatingWrite(id, 7, null, batch.id, 2, now))
        val item = RatedTitle(title.id, id, TmdbMedia.MOVIE)
        val match = RatedLookup.Matched(811234, listOf(18), "en", listOf("US"))
        assertThat(service.record(item, match, false)).isEqualTo("matched")
        assertThat(service.record(item, match, false)).isEqualTo("skipped")
        assertThat(jdbc.sql("SELECT count(*) FROM rated_title_tmdb_metadata WHERE catalog_title_id=:id").param("id", title.id)
            .query(Long::class.java).single()).isEqualTo(1)
        assertThat(service.pending(20, false).map { it.titleId }).doesNotContain(title.id)
    }

    @Test
    fun `rated title enrichment treats claimed TMDB ID as conflict and permits explicit retry`() {
        val service = context.getBean(RatedTitleEnrichment::class.java)
        val first = title().copy(kind = TitleKind.MOVIE)
        val second = title().copy(kind = TitleKind.MOVIE)
        val id1 = imdbId()
        val id2 = imdbId()
        for ((t, id) in listOf(first to id1, second to id2)) {
            catalog.save(t)
            catalog.attachExternalId(t.id, ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, id), "fixture", now)
        }
        val item1 = RatedTitle(first.id, id1, TmdbMedia.MOVIE)
        val item2 = RatedTitle(second.id, id2, TmdbMedia.MOVIE)
        val match = RatedLookup.Matched(811235, emptyList(), null, emptyList())
        assertThat(service.record(item1, match, false)).isEqualTo("matched")
        assertThat(service.record(item2, match, false)).isEqualTo("conflict")
        assertThat(service.record(item2, RatedLookup.Matched(811236, emptyList(), null, emptyList()), true)).isEqualTo("matched")
    }

    @Test
    fun `concurrent rated enrichment converges and transaction rollback leaves no metadata`() {
        val service = context.getBean(RatedTitleEnrichment::class.java)
        val title = title().copy(kind = TitleKind.SERIES)
        val id = imdbId()
        catalog.save(title)
        catalog.attachExternalId(title.id, ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, id), "fixture", now)
        val item = RatedTitle(title.id, id, TmdbMedia.TV)
        val match = RatedLookup.Matched(811237, emptyList(), null, emptyList())
        assertThatThrownBy { transactions.executeWithoutResult {
            service.record(item, match, false)
            throw IllegalStateException("synthetic rollback")
        } }.isInstanceOf(IllegalStateException::class.java)
        assertThat(jdbc.sql("SELECT count(*) FROM rated_title_enrichment_attempt WHERE catalog_title_id=:id")
            .param("id", title.id).query(Long::class.java).single()).isZero()
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val jobs = (1..2).map { pool.submit<String> {
                check(start.await(5, TimeUnit.SECONDS))
                service.record(item, match, false)
            } }
            start.countDown()
            assertThat(jobs.map { it.get(15, TimeUnit.SECONDS) }).containsExactlyInAnyOrder("matched", "skipped")
        }
        assertThat(jdbc.sql("SELECT count(*) FROM rated_title_tmdb_metadata WHERE catalog_title_id=:id")
            .param("id", title.id).query(Long::class.java).single()).isEqualTo(1)
    }

    @Test
    fun `recommendation snapshot excludes rated and uncertain identities and is repeatable`() {
        val service = context.getBean(RatedTitleEnrichment::class.java)
        val repo = context.getBean(RecommendationRepository::class.java)
        val uniqueGenre = 991001L
        val rated = (1..3).map { index ->
            val title = title().copy(kind = TitleKind.MOVIE)
            val id = imdbId()
            catalog.save(title)
            catalog.attachExternalId(title.id, ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, id), "fixture", now)
            val import = batch()
            history.tryCreateBatch(import)
            history.saveRating(RatingWrite(id, 10, null, import.id, 2, now))
            service.record(RatedTitle(title.id, id, TmdbMedia.MOVIE),
                RatedLookup.Matched(990100L + index, listOf(uniqueGenre), "en", listOf("US")), false)
            title to id
        }
        // A stale discovery row must not bypass a later exact TMDB catalog link.
        catalog.attachExternalId(rated[2].first.id, ExternalTitleId(ExternalIdNamespace.TMDB_MOVIE, "990203"), "fixture", now)
        val nonRated = title().copy(kind = TitleKind.MOVIE)
        catalog.save(nonRated)
        catalog.attachExternalId(nonRated.id, ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, imdbId()), "fixture", now)
        catalog.attachExternalId(nonRated.id, ExternalTitleId(ExternalIdNamespace.TMDB_MOVIE, "990204"), "fixture", now)

        val day = LocalDate.parse("2042-01-10")
        fun insert(id: Long, imdb: String?, status: String = "unmatched", date: LocalDate? = day, media: String = "movie") {
            jdbc.sql("""
                INSERT INTO tmdb_candidate(media_type, tmdb_id, title, primary_date, original_language,
                    genre_ids, origin_countries, imdb_id, link_status, first_seen_at, last_seen_at)
                VALUES (:media, :id, 'Synthetic candidate', :date, 'en', '{991001}', '{US}', :imdb, :status, :now, :now)
            """.trimIndent()).param("media", media).param("id", id).param("date", date, java.sql.Types.DATE)
                .param("imdb", imdb, java.sql.Types.VARCHAR).param("status", status)
                .param("now", java.time.OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC)).update()
        }
        insert(990200, imdbId()) // eligible
        insert(990201, rated[0].second) // exact IMDb rating
        insert(990102, imdbId()) // matches a rated TMDB metadata ID
        insert(990203, imdbId()) // matches a rated catalog TMDB ID
        insert(990204, imdbId()) // inconsistent catalog IMDb identity
        insert(990205, imdbId(), "not_checked")
        insert(990206, imdbId(), "conflict")
        insert(990207, null)
        insert(990208, imdbId(), date = null)
        insert(990209, imdbId(), date = day.plusDays(1))
        insert(990210, imdbId(), media = "tv") // movie evidence cannot establish series support

        val before = jdbc.sql("SELECT count(*) FROM flyway_schema_history").query(Long::class.java).single()
        val first = repo.preview(day, day, 10)
        val second = repo.preview(day, day, 10)
        assertThat(first).isEqualTo(second)
        assertThat(first.map { it.candidate.tmdbId }).containsExactly(990200L)
        assertThat(first.single().score).isEqualTo(1_000_000)
        assertThat(jdbc.sql("SELECT count(*) FROM flyway_schema_history").query(Long::class.java).single()).isEqualTo(before)
        assertThat(jdbc.sql("SELECT count(*) FROM tmdb_candidate WHERE tmdb_id BETWEEN 990200 AND 990210")
            .query(Long::class.java).single()).isEqualTo(10)
        assertThatThrownBy { repo.preview(day, day.minusDays(1), 5) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { repo.preview(day, day.plusDays(31), 5) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { repo.preview(day, day, 0) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { repo.preview(day, day, 11) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `concurrent LINE redelivery records feedback once`() {
        val repo = lineFeedbackRepository()
        val item = notificationItem(998701)
        val event = LinePostbackEvent("fixture-line-repeat", item, "interested", now.toEpochMilli())
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val jobs = (1..2).map { pool.submit {
                check(start.await(5, TimeUnit.SECONDS))
                repo.accept(event)
            } }
            start.countDown()
            jobs.forEach { it.get(15, TimeUnit.SECONDS) }
        }
        assertThat(jdbc.sql("SELECT outcome FROM line_webhook_event WHERE event_id = :event")
            .param("event", event.id).query(String::class.java).list()).containsExactly("applied")
        assertThat(jdbc.sql("SELECT action FROM feedback_event WHERE item_id = :item")
            .param("item", item).query(String::class.java).list()).containsExactly("interested")
        assertThat(jdbc.sql("SELECT event_id FROM user_title_state WHERE media_type = 'tv' AND tmdb_id = 998701")
            .query(String::class.java).list()).containsExactly(event.id)
    }

    @Test
    fun `LINE feedback orders by event time and ID and ignores unknown targets`() {
        val repo = lineFeedbackRepository()
        val item = notificationItem(998702)
        val time = now.toEpochMilli()
        repo.accept(LinePostbackEvent("fixture-line-order-b", item, "interested", time))
        repo.accept(LinePostbackEvent("fixture-line-order-a", item, "not_interested", time))
        repo.accept(LinePostbackEvent("fixture-line-order-z", item, "not_interested", time - 1))
        assertThat(jdbc.sql("SELECT event_id FROM user_title_state WHERE media_type = 'tv' AND tmdb_id = 998702")
            .query(String::class.java).list()).containsExactly("fixture-line-order-b")
        repo.accept(LinePostbackEvent("fixture-line-order-c", item, "watched", time))
        val unknown = "fixture-line-unknown"
        repo.accept(LinePostbackEvent(unknown, UUID.randomUUID(), "watched", time + 1))

        assertThat(jdbc.sql("SELECT action FROM user_title_state WHERE media_type = 'tv' AND tmdb_id = 998702")
            .query(String::class.java).list()).containsExactly("watched")
        assertThat(jdbc.sql("SELECT event_id FROM user_title_state WHERE media_type = 'tv' AND tmdb_id = 998702")
            .query(String::class.java).list()).containsExactly("fixture-line-order-c")
        assertThat(jdbc.sql("SELECT count(*) FROM feedback_event WHERE item_id = :item")
            .param("item", item).query(Long::class.java).single()).isEqualTo(4)
        assertThat(jdbc.sql("SELECT outcome FROM line_webhook_event WHERE event_id = :event")
            .param("event", unknown).query(String::class.java).list()).containsExactly("ignored")
        assertThat(jdbc.sql("SELECT count(*) FROM feedback_event WHERE event_id = :event")
            .param("event", unknown).query(Long::class.java).single()).isZero()
    }

    @Test
    fun `LINE feedback rolls back the event and state together and permits redelivery`() {
        val repo = lineFeedbackRepository()
        val item = notificationItem(998703)
        val event = LinePostbackEvent("fixture-line-rollback", item, "watched", now.toEpochMilli())
        assertThatThrownBy { transactions.executeWithoutResult {
            repo.accept(event)
            throw IllegalStateException("synthetic rollback after persisting feedback")
        } }.isInstanceOf(IllegalStateException::class.java)
        for (table in listOf("line_webhook_event", "feedback_event", "user_title_state")) {
            assertThat(jdbc.sql("SELECT count(*) FROM $table WHERE event_id = :event")
                .param("event", event.id).query(Long::class.java).single()).isZero()
        }
        repo.accept(event)
        assertThat(jdbc.sql("SELECT action FROM user_title_state WHERE event_id = :event")
            .param("event", event.id).query(String::class.java).list()).containsExactly("watched")
    }

    private fun lineFeedbackRepository() = LineFeedbackRepository(jdbc, context.getBean(PlatformTransactionManager::class.java))

    private fun notificationItem(tmdbId: Long): UUID {
        val delivery = UUID.randomUUID()
        val item = UUID.randomUUID()
        val timestamp = java.sql.Timestamp.from(now)
        jdbc.sql("""
            INSERT INTO tmdb_candidate(media_type, tmdb_id, title, link_status, first_seen_at, last_seen_at)
            VALUES ('tv', :tmdb, 'Synthetic feedback title', 'not_checked', :time, :time)
        """.trimIndent()).param("tmdb", tmdbId).param("time", timestamp).update()
        jdbc.sql("""
            INSERT INTO notification_delivery(id, retry_key, recipient_id, payload, status, created_at)
            VALUES (:id, :retry, :recipient, '[{"type":"text","text":"Synthetic delivery"}]', 'accepted', :time)
        """.trimIndent()).param("id", delivery).param("retry", UUID.randomUUID())
            .param("recipient", "U" + "1".repeat(32)).param("time", timestamp).update()
        jdbc.sql("""
            INSERT INTO notification_item(id, delivery_id, media_type, tmdb_id, title, position)
            VALUES (:id, :delivery, 'tv', :tmdb, 'Synthetic feedback title', 1)
        """.trimIndent()).param("id", item).param("delivery", delivery).param("tmdb", tmdbId).update()
        return item
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
