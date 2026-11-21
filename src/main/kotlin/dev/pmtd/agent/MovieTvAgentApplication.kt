package dev.pmtd.agent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import dev.pmtd.agent.imdb.ImdbImportService
import dev.pmtd.agent.imdb.ImdbHistoryRepository
import org.springframework.core.env.MapPropertySource
import dev.pmtd.agent.catalog.CatalogRepository
import dev.pmtd.agent.discovery.TmdbDiscovery
import dev.pmtd.agent.discovery.TmdbPreview
import dev.pmtd.agent.discovery.RatedTitleEnrichment
import dev.pmtd.agent.discovery.TmdbImport
import dev.pmtd.agent.discovery.TmdbMedia
import dev.pmtd.agent.discovery.fetchTmdbBatch
import java.time.Instant
import dev.pmtd.agent.discovery.parseTmdbDate
import dev.pmtd.agent.discovery.validateTmdbWindow
import dev.pmtd.agent.discovery.runTvmaze
import dev.pmtd.agent.discovery.TvmazeDiscovery
import dev.pmtd.agent.discovery.TvmazeScheduleImport
import dev.pmtd.agent.recommendation.RecommendationRepository
import java.time.LocalDate
import java.nio.file.Path

@SpringBootApplication
class MovieTvAgentApplication

fun main(args: Array<String>) {
    if (args.any { it.startsWith("--recommend-") }) {
        runRecommendationPreview(args)
        return
    }
    if (args.any { it.startsWith("--tmdb-enrich-") }) {
        runTmdbEnrichment(args)
        return
    }
    if (args.any { it.startsWith("--tmdb-import-") }) {
        runTmdbImport(args)
        return
    }
    if (args.any { it.startsWith("--tmdb-preview-") }) {
        runTmdbPreview(args)
        return
    }
    if (args.any { it == "--imdb-stats" }) {
        require(args.count { it == "--imdb-stats" } == 1 &&
            args.none { it.startsWith("--import-imdb=") || it.startsWith("--tvmaze-") }) {
            "Use --imdb-stats once and without another command"
        }
        val appArgs = args.filterNot { it == "--imdb-stats" }.toTypedArray()
        val context = SpringApplicationBuilder(MovieTvAgentApplication::class.java)
            .web(WebApplicationType.NONE)
            .initializers({ it.environment.propertySources.addFirst(
                MapPropertySource("statsCommand", mapOf("spring.flyway.enabled" to false))
            ) }).run(*appArgs)
        try {
            require(context.environment.activeProfiles.contains("postgres")) { "The postgres profile is required" }
            val counts = context.getBean(ImdbHistoryRepository::class.java).ratingDistribution()
            println(formatRatingDistribution(counts))
        } finally {
            context.close()
        }
        return
    }
    if (args.any { it.startsWith("--tvmaze-import-") }) {
        require(args.none { it == "--imdb-stats" || it.startsWith("--import-imdb=") ||
            (it.startsWith("--tvmaze-") && !it.startsWith("--tvmaze-import-")) }) { "Use only one command" }
        val command = args.filter { it.startsWith("--tvmaze-import-") }
        val pairs = command.map { it.substringBefore('=') to it.substringAfter('=', "") }
        require(pairs.size == 2 && pairs.map { it.first }.toSet() ==
            setOf("--tvmaze-import-country", "--tvmaze-import-date")) { "Provide country and date exactly once" }
        val options = pairs.toMap()
        val country = options.getValue("--tvmaze-import-country")
        val date = LocalDate.parse(options.getValue("--tvmaze-import-date"))
        require(country.matches(Regex("[A-Z]{2}"))) { "Country must be a two-letter uppercase ISO code" }
        val context = SpringApplicationBuilder(MovieTvAgentApplication::class.java)
            .web(WebApplicationType.NONE)
            .initializers({ it.environment.propertySources.addFirst(
                MapPropertySource("tvmazeImportCommand", mapOf("spring.flyway.enabled" to false))
            ) }).run(*args.filterNot { it.startsWith("--tvmaze-import-") }.toTypedArray())
        try {
            require(context.environment.activeProfiles.contains("postgres")) { "The postgres profile is required" }
            val episodes = TvmazeDiscovery().fetch(country, date)
            val count = context.getBean(TvmazeScheduleImport::class.java).save(country, date, episodes)
            println("TV data supplied by TVmaze (https://www.tvmaze.com/), CC BY-SA.")
            println("Observed $count episode(s) for $country on $date.")
        } finally {
            context.close()
        }
        return
    }
    if (args.any { it.startsWith("--tvmaze-") }) {
        runTvmaze(args)
        return
    }
    val importArgs = args.filter { it.startsWith("--import-imdb=") }
    if (importArgs.isEmpty()) {
        runApplication<MovieTvAgentApplication>(*args)
        return
    }
    require(importArgs.size == 1 && importArgs.single().substringAfter('=').isNotBlank()) {
        "Provide exactly one nonempty --import-imdb path"
    }
    val appArgs = args.filterNot { it.startsWith("--import-imdb=") }.toTypedArray()
    val context = SpringApplicationBuilder(MovieTvAgentApplication::class.java)
        .web(WebApplicationType.NONE).run(*appArgs)
    try {
        require(context.environment.activeProfiles.contains("postgres")) { "The postgres profile is required" }
        val result = try {
            context.getBean(ImdbImportService::class.java)
                .import(Path.of(importArgs.single().substringAfter('=')))
        } catch (_: Exception) {
            throw IllegalStateException("Import failed. Check the export format and database connection.")
        }
        val invalid = if (result.alreadyImported) 0 else result.counts.invalid - result.skipped
        println("Imported: ${result.counts.inserted}, updated: ${result.counts.updated}, unchanged: ${result.counts.unchanged}, invalid: $invalid, skipped: ${result.skipped}")
        if (result.alreadyImported) println("This export was already imported.")
        result.issues.forEach { println("Row ${it.row}: ${it.reason}") }
    } finally {
        context.close()
    }
}

private fun runRecommendationPreview(args: Array<String>) {
    require(args.none { it == "--imdb-stats" || it.startsWith("--import-imdb") ||
        it.startsWith("--tvmaze-") || it.startsWith("--tmdb-") }) { "Use only one command" }
    val command = args.filter { it.startsWith("--recommend-") }
    val pairs = command.map { it.substringBefore('=') to it.substringAfter('=', "") }
    val keys = pairs.map { it.first }
    require(keys.count { it == "--recommend-from" } == 1 && keys.count { it == "--recommend-to" } == 1 &&
        keys.count { it == "--recommend-limit" } <= 1 && keys.size in 2..3 &&
        keys.all { it in setOf("--recommend-from", "--recommend-to", "--recommend-limit") }) {
        "Provide one from date, one to date, and an optional limit"
    }
    val options = pairs.toMap()
    val from = parseTmdbDate(options.getValue("--recommend-from"))
    val to = parseTmdbDate(options.getValue("--recommend-to"))
    validateTmdbWindow(from, to)
    val limit = options["--recommend-limit"]?.toIntOrNull() ?: if ("--recommend-limit" in options) null else 5
    require(limit != null && limit in 1..10) { "Recommendation limit must be between 1 and 10" }
    val appArgs = args.filterNot { it.startsWith("--recommend-") }.toTypedArray()
    val recommendations = try {
        SpringApplicationBuilder(MovieTvAgentApplication::class.java).web(WebApplicationType.NONE)
            .initializers({ context -> context.environment.propertySources.addFirst(
                MapPropertySource("recommendationPreviewCommand", mapOf(
                    "spring.flyway.enabled" to false,
                    "spring.sql.init.mode" to "never",
                    "spring.datasource.hikari.read-only" to true,
                )),
            ) }).run(*appArgs).use { context ->
                require("postgres" in context.environment.activeProfiles) { "The postgres profile is required" }
                context.getBean(RecommendationRepository::class.java).preview(from, to, limit)
            }
    } catch (_: Exception) {
        throw IllegalStateException("Recommendation preview failed. Check the postgres profile and database configuration.")
    }
    println("This product uses the TMDB API but is not endorsed or certified by TMDB.")
    if (recommendations.isEmpty()) {
        println("No recommendations meet the relevance and identity requirements.")
    } else {
        recommendations.forEachIndexed { index, result ->
            val item = result.candidate
            val title = item.title.replace(Regex("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]"), " ").trim()
            val dateLabel = if (item.media == "movie") "primary release" else "series premiere"
            println("${index + 1}. $title - $dateLabel ${item.date}. Shares supported genres with highly rated history.")
        }
    }
}

private fun runTmdbEnrichment(args: Array<String>) {
    val commands = args.filter { it.startsWith("--tmdb-enrich-") }
    require(args.none { it == "--imdb-stats" || it.startsWith("--import-imdb=") ||
        it.startsWith("--tvmaze-") || it.startsWith("--tmdb-import-") || it.startsWith("--tmdb-preview-") }) { "Use only one command" }
    val limits = commands.filter { it.startsWith("--tmdb-enrich-limit=") }
    require(limits.size == 1 && commands.count { it == "--tmdb-enrich-retry" } <= 1 &&
        commands.size == limits.size + commands.count { it == "--tmdb-enrich-retry" }) { "Provide one enrichment limit and optional retry flag" }
    val limit = limits.single().substringAfter('=').toIntOrNull()
    require(limit != null && limit in 1..20) { "Limit must be between 1 and 20" }
    val retry = "--tmdb-enrich-retry" in commands
    val appArgs = args.filterNot { it.startsWith("--tmdb-enrich-") }.toTypedArray()
    SpringApplicationBuilder(MovieTvAgentApplication::class.java).web(WebApplicationType.NONE)
        .initializers({ context -> context.environment.propertySources.addFirst(
            MapPropertySource("tmdbEnrichmentCommand", mapOf("spring.flyway.enabled" to false, "spring.sql.init.mode" to "never"))
        ) }).run(*appArgs).use { context ->
            require("postgres" in context.environment.activeProfiles) { "The postgres profile is required" }
            val token = context.environment.getProperty("PMTD_TMDB_READ_TOKEN")
            require(!token.isNullOrBlank()) { "Set PMTD_TMDB_READ_TOKEN locally" }
            val service = context.getBean(RatedTitleEnrichment::class.java)
            val client = TmdbDiscovery(token, requestLimit = 40)
            val counts = mutableMapOf<String, Int>()
            try {
                for (item in service.pending(limit, retry)) {
                    val outcome = service.record(item, client.ratedMetadata(item.imdbId, item.media), retry)
                    counts[outcome] = (counts[outcome] ?: 0) + 1
                }
            } catch (_: Exception) {
                throw IllegalStateException("Enrichment stopped. Check TMDB access, request limits and database configuration. Previously completed titles remain saved.")
            }
            println("This product uses the TMDB API but is not endorsed or certified by TMDB.")
            println("Matched: ${counts["matched"] ?: 0}, unmatched: ${counts["unmatched"] ?: 0}, conflicts: ${counts["conflict"] ?: 0}, skipped: ${counts["skipped"] ?: 0}")
        }
}

private fun runTmdbImport(args: Array<String>) {
    require(args.none { it == "--imdb-stats" || it.startsWith("--import-imdb=") ||
        it.startsWith("--tvmaze-") || it.startsWith("--tmdb-preview-") }) { "Use only one command" }
    val command = args.filter { it.startsWith("--tmdb-import-") }
    require(command.size == 2 && command.map { it.substringBefore('=') }.toSet() ==
        setOf("--tmdb-import-from", "--tmdb-import-to")) { "Provide from and to exactly once" }
    val options = command.associate { it.substringBefore('=') to it.substringAfter('=', "") }
    val from = parseTmdbDate(options.getValue("--tmdb-import-from"))
    val to = parseTmdbDate(options.getValue("--tmdb-import-to"))
    validateTmdbWindow(from, to)
    val appArgs = args.filterNot { it.startsWith("--tmdb-import-") }.toTypedArray()
    SpringApplicationBuilder(MovieTvAgentApplication::class.java).web(WebApplicationType.NONE)
        .initializers({ context -> context.environment.propertySources.addFirst(
            MapPropertySource("tmdbImportCommand", mapOf("spring.flyway.enabled" to false, "spring.sql.init.mode" to "never"))
        ) }).run(*appArgs).use { context ->
            require("postgres" in context.environment.activeProfiles) { "The postgres profile is required" }
            val token = context.environment.getProperty("PMTD_TMDB_READ_TOKEN")
            require(!token.isNullOrBlank()) { "Set PMTD_TMDB_READ_TOKEN locally" }
            val started = Instant.now()
            val discovery = TmdbDiscovery(token)
            val movies = fetchTmdbBatch(discovery, TmdbMedia.MOVIE, from, to)
            val shows = fetchTmdbBatch(discovery, TmdbMedia.TV, from, to)
            val result = context.getBean(TmdbImport::class.java).save(from, to, started, movies, shows)
            println("This product uses the TMDB API but is not endorsed or certified by TMDB.")
            println("Observed: ${result.candidates}; conflicts: ${result.conflicts}")
            println("Movie pages: ${movies.results.pagesFetched}; truncated: ${movies.results.morePages}")
            println("TV pages: ${shows.results.pagesFetched}; truncated: ${shows.results.morePages}")
            println("Identity checks limited to 10 candidates per media type.")
        }
}

private fun runTmdbPreview(args: Array<String>) {
    require(args.none { it == "--imdb-stats" || it.startsWith("--import-imdb=") || it.startsWith("--tvmaze-") || it.startsWith("--tmdb-import-") }) {
        "Use only one command"
    }
    val previewArgs = args.filter { it.startsWith("--tmdb-preview-") }
    val keys = previewArgs.map { it.substringBefore('=') }
    require(keys.size == 2 && keys.toSet() == setOf("--tmdb-preview-from", "--tmdb-preview-to")) {
        "Provide --tmdb-preview-from and --tmdb-preview-to exactly once"
    }
    val values = previewArgs.associate { it.substringBefore('=') to it.substringAfter('=', "") }
    val from = parseTmdbDate(values.getValue("--tmdb-preview-from"))
    val to = parseTmdbDate(values.getValue("--tmdb-preview-to"))
    validateTmdbWindow(from, to)

    val appArgs = args.filterNot { it.startsWith("--tmdb-preview-") }.toTypedArray()
    SpringApplicationBuilder(MovieTvAgentApplication::class.java)
        .web(WebApplicationType.NONE)
        .initializers({ context ->
            context.environment.propertySources.addFirst(
                MapPropertySource("tmdbPreviewCommand", mapOf(
                    "spring.flyway.enabled" to false,
                    "spring.sql.init.mode" to "never",
                    "spring.datasource.hikari.read-only" to true,
                )),
            )
        }).run(*appArgs).use { context ->
            require("postgres" in context.environment.activeProfiles) { "The postgres profile is required" }
            val token = context.environment.getProperty("PMTD_TMDB_READ_TOKEN")
            require(!token.isNullOrBlank()) { "Set PMTD_TMDB_READ_TOKEN locally before running the preview" }
            println(TmdbPreview(TmdbDiscovery(token), context.getBean(CatalogRepository::class.java)).render(from, to))
        }
}

internal fun formatRatingDistribution(counts: Map<Int, Long>): String =
    (listOf("Rating distribution:") + (1..10).map { rating -> "$rating: ${counts[rating] ?: 0}" }).joinToString("\n")
