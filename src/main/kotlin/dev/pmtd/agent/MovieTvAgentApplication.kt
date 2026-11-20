package dev.pmtd.agent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import dev.pmtd.agent.imdb.ImdbImportService
import dev.pmtd.agent.discovery.runTvmaze
import java.nio.file.Path

@SpringBootApplication
class MovieTvAgentApplication

fun main(args: Array<String>) {
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
