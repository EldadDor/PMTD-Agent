package dev.pmtd.agent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import dev.pmtd.agent.imdb.ImdbImportService
import dev.pmtd.agent.imdb.ImdbHistoryRepository
import org.springframework.core.env.MapPropertySource
import dev.pmtd.agent.discovery.runTvmaze
import dev.pmtd.agent.discovery.TvmazeDiscovery
import dev.pmtd.agent.discovery.TvmazeScheduleImport
import java.time.LocalDate
import java.nio.file.Path

@SpringBootApplication
class MovieTvAgentApplication

fun main(args: Array<String>) {
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

internal fun formatRatingDistribution(counts: Map<Int, Long>): String =
    (listOf("Rating distribution:") + (1..10).map { rating -> "$rating: ${counts[rating] ?: 0}" }).joinToString("\n")
