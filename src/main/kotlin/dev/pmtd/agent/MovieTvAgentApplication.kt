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
import dev.pmtd.agent.recommendation.AudienceDecision
import dev.pmtd.agent.recommendation.ImdbRatingsSnapshot
import dev.pmtd.agent.recommendation.classifyAudience
import dev.pmtd.agent.notification.LineDelivery
import dev.pmtd.agent.notification.LineGateway
import dev.pmtd.agent.notification.LinePushResult
import org.springframework.web.client.RestClient
import java.util.UUID
import java.time.LocalDate
import java.nio.file.Path

@SpringBootApplication
class MovieTvAgentApplication

fun main(args: Array<String>) {
    if (args.any { it.startsWith("--line-") }) {
        runLineDelivery(args)
        return
    }
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

private fun runLineDelivery(args: Array<String>) {
    val options = args.filter { it.startsWith("--line-") }
    val keys = options.map { it.substringBefore('=') }
    val allowed = setOf("--line-prepare-from", "--line-prepare-to", "--line-imdb-ratings", "--line-imdb-date", "--line-send")
    require(keys.distinct().size == keys.size && keys.all { it in allowed }) { "Invalid LINE command" }
    val values = options.associate { it.substringBefore('=') to it.substringAfter('=', "") }
    val preparing = "--line-prepare-from" in values
    require(if (preparing) keys.toSet() == allowed - "--line-send" else keys.toSet() == setOf("--line-send")) {
        "Provide the preparation dates and IMDb snapshot or one delivery ID to send"
    }
    require(args.none { it.startsWith("--recommend-") || it.startsWith("--tmdb-") ||
        it.startsWith("--tvmaze-") || it.startsWith("--import-imdb") || it == "--imdb-stats" }) { "Use only one command" }
    val appArgs = args.filterNot { it.startsWith("--line-") }.toTypedArray()
    SpringApplicationBuilder(MovieTvAgentApplication::class.java).web(WebApplicationType.NONE)
        .initializers({ context -> context.environment.propertySources.addFirst(
            MapPropertySource("lineCommand", mapOf("spring.flyway.enabled" to false, "spring.sql.init.mode" to "never")),
        ) }).run(*appArgs).use { context ->
            require("postgres" in context.environment.activeProfiles) { "The postgres profile is required" }
            val recipient = context.environment.getRequiredProperty("PMTD_LINE_USER_ID")
            require(recipient.matches(Regex("U[0-9a-f]{32}"))) { "Invalid LINE recipient" }
            val delivery = LineDelivery(context.getBean(org.springframework.jdbc.core.simple.JdbcClient::class.java),
                context.getBean(org.springframework.transaction.PlatformTransactionManager::class.java))
            if (preparing) {
                val from = parseTmdbDate(values.getValue("--line-prepare-from"))
                val to = parseTmdbDate(values.getValue("--line-prepare-to"))
                val date = parseTmdbDate(values.getValue("--line-imdb-date"))
                val today = LocalDate.now(java.time.ZoneOffset.UTC)
                require(date in today.minusDays(2)..today) { "A current IMDb snapshot is required" }
                val ranked = context.getBean(RecommendationRepository::class.java).preview(from, to)
                val snapshot = ImdbRatingsSnapshot.load(Path.of(values.getValue("--line-imdb-ratings")), date,
                    ranked.mapNotNull { it.candidate.imdbId }.toSet())
                val eligible = ranked.filter { classifyAudience(it.candidate, snapshot, today) == AudienceDecision.SHORTLIST }
                    .sortedByDescending { (snapshot.ratings[it.candidate.imdbId]?.votes ?: 0) >= 1000 }.take(5)
                if (eligible.isEmpty()) println("No eligible recommendations to prepare.")
                else {
                    val (id, payload) = delivery.prepare(recipient, eligible)
                    println("Delivery $id")
                    println(payload)
                }
            } else {
                val id = UUID.fromString(values.getValue("--line-send"))
                val token = context.environment.getRequiredProperty("PMTD_LINE_ACCESS_TOKEN")
                val factory = org.springframework.http.client.SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(java.time.Duration.ofSeconds(5))
                    setReadTimeout(java.time.Duration.ofSeconds(10))
                }
                val client = RestClient.builder().baseUrl("https://api.line.me").requestFactory(factory).build()
                val mapper = tools.jackson.databind.json.JsonMapper.builder().build()
                fun quota(path: String) = client.get().uri(path).headers { it.setBearerAuth(token) }
                    .retrieve().body(String::class.java)?.let(mapper::readTree) ?: error("LINE quota unavailable")
                val limit = quota("/v2/bot/message/quota")
                val usage = quota("/v2/bot/message/quota/consumption")
                require(limit.path("type").asText() == "none" ||
                    (limit.path("type").asText() == "limited" && limit.path("value").isIntegralNumber &&
                        usage.path("totalUsage").isIntegralNumber && usage.path("totalUsage").asLong() < limit.path("value").asLong())) {
                    "LINE quota unavailable or exhausted"
                }
                val result = delivery.send(id, recipient, LineGateway(token, recipient, client))
                println("LINE delivery result: ${result.name.lowercase()}")
                if (result != LinePushResult.ACCEPTED) println("Review delivery status before any further action.")
            }
        }
}

private fun runRecommendationPreview(args: Array<String>) {
    require(args.none { it == "--imdb-stats" || it.startsWith("--import-imdb") ||
        it.startsWith("--tvmaze-") || it.startsWith("--tmdb-") }) { "Use only one command" }
    val command = args.filter { it.startsWith("--recommend-") }
    val pairs = command.map { it.substringBefore('=') to it.substringAfter('=', "") }
    val keys = pairs.map { it.first }
    val allowed = setOf("--recommend-from", "--recommend-to", "--recommend-limit", "--recommend-imdb-ratings",
        "--recommend-imdb-date", "--recommend-tmdb-context")
    require(keys.size == keys.toSet().size && keys.all { it in allowed } &&
        "--recommend-from" in keys && "--recommend-to" in keys) {
        "Provide one from date, one to date, and each optional argument at most once"
    }
    val options = pairs.toMap()
    val from = parseTmdbDate(options.getValue("--recommend-from"))
    val to = parseTmdbDate(options.getValue("--recommend-to"))
    validateTmdbWindow(from, to)
    val limit = options["--recommend-limit"]?.toIntOrNull() ?: if ("--recommend-limit" in options) null else 5
    require(limit != null && limit in 1..10) { "Recommendation limit must be between 1 and 10" }
    val snapshotFile = options["--recommend-imdb-ratings"]
    val snapshotDate = options["--recommend-imdb-date"]?.let(::parseTmdbDate)
    val today = LocalDate.now(java.time.ZoneOffset.UTC)
    require((snapshotFile == null) == (snapshotDate == null) && snapshotFile?.isBlank() != true) {
        "Provide both the IMDb snapshot file and its date"
    }
    require(snapshotDate == null || snapshotDate <= today) { "IMDb snapshot date cannot be in the future" }
    val includeTmdb = "--recommend-tmdb-context" in command
    require("--recommend-tmdb-context" !in options || includeTmdb) { "Use --recommend-tmdb-context without a value" }
    val appArgs = args.filterNot { it.startsWith("--recommend-") }.toTypedArray()
    val (candidates, tmdb) = try {
        SpringApplicationBuilder(MovieTvAgentApplication::class.java).web(WebApplicationType.NONE)
            .initializers({ context -> context.environment.propertySources.addFirst(
                MapPropertySource("recommendationPreviewCommand", mapOf(
                    "spring.flyway.enabled" to false,
                    "spring.sql.init.mode" to "never",
                    "spring.datasource.hikari.read-only" to true,
                )),
            ) }).run(*appArgs).use { context ->
                require("postgres" in context.environment.activeProfiles) { "The postgres profile is required" }
                val provider = if (includeTmdb) TmdbDiscovery(
                    context.environment.getRequiredProperty("PMTD_TMDB_READ_TOKEN"), requestLimit = limit * 3,
                ) else null
                context.getBean(RecommendationRepository::class.java).preview(from, to) to provider
            }
    } catch (_: Exception) {
        throw IllegalStateException("Recommendation preview failed. Check the postgres configuration and any requested TMDB token.")
    }
    val snapshot = if (snapshotFile != null && snapshotDate != null) {
        ImdbRatingsSnapshot.load(Path.of(snapshotFile), snapshotDate, candidates.mapNotNull { it.candidate.imdbId }.toSet())
    } else null
    val decisions = candidates.groupBy { classifyAudience(it.candidate, snapshot, today) }
    println("This product uses the TMDB API but is not endorsed or certified by TMDB.")
    if (snapshot != null) println("Information courtesy of IMDb (https://www.imdb.com). Used with permission.")
    val shortlist = decisions[AudienceDecision.SHORTLIST].orEmpty().sortedByDescending {
        (snapshot?.ratings?.get(it.candidate.imdbId)?.votes ?: 0L) >= 1_000
    }.take(limit)
    val review = decisions[AudienceDecision.REVIEW].orEmpty().take(limit)
    if (shortlist.isEmpty()) println("No recommendations meet the shortlist requirements.")
    for ((heading, results) in listOf("Shortlist" to shortlist, "For manual review" to review)) {
        if (results.isEmpty()) continue
        println(heading)
        results.forEachIndexed { index, result ->
            val item = result.candidate
            val title = item.title.replace(Regex("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]"), " ").trim()
            val dateLabel = if (item.media == "movie") "primary release" else "series premiere"
            println("${index + 1}. $title - $dateLabel ${item.date}. Shares supported genres with highly rated history.")
            val rating = snapshot?.ratings?.get(item.imdbId)
            if (snapshot != null && rating != null) println("   IMDb ${rating.score}/10, ${rating.votes} votes (snapshot ${snapshot.date}).")
            else println("   IMDb audience rating unavailable.")
            val tmdbPage = "https://www.themoviedb.org/${item.media}/${item.tmdbId}"
            println("   Synopsis: $tmdbPage")
            if (heading == "For manual review") {
                println("   Reviews: $tmdbPage/reviews")
                if (tmdb != null) {
                    val media = if (item.media == "movie") TmdbMedia.MOVIE else TmdbMedia.TV
                    val context = runCatching { tmdb.audienceContext(media, item.tmdbId) }.getOrNull()
                    if (context == null) println("   TMDB context unavailable.")
                    else {
                        if (context.score != null) println("   TMDB ${context.score}/10, ${context.votes} votes.")
                        if (context.synopsis != null) println("   ${context.synopsis}")
                    }
                }
            }
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
