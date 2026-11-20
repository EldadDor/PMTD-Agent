package dev.pmtd.agent.discovery

import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.LocalDate

class TvmazeDiscovery(
    private val client: RestClient = defaultClient(),
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
) {
    fun schedule(country: String, date: LocalDate, limit: Int): List<Episode> {
        require(country.matches(Regex("[A-Z]{2}"))) { "Country must be a two-letter uppercase ISO code" }
        require(limit in 1..100) { "Limit must be between 1 and 100" }
        return fetch(country, date).take(limit)
    }

    fun fetch(country: String, date: LocalDate): List<Episode> {
        require(country.matches(Regex("[A-Z]{2}"))) { "Country must be a two-letter uppercase ISO code" }
        val bytes = request(country, date)
        val root = ObjectMapper().readTree(bytes)
        require(root.isArray) { "Invalid TVmaze schedule" }
        // Validate the full response before printing any results.
        val episodes = (0 until root.size()).map { index ->
            val node = root.path(index)
            require(node.isObject) { "Invalid schedule entry" }
            val show = node.path("show")
            require(show.isObject) { "Missing show" }
            Episode(id(node, "id"), id(show, "id"), field(show, "name"), field(node, "name"),
                field(node, "airdate"), field(show, "url"))
        }.toList()
        return episodes
    }

    private fun request(country: String, date: LocalDate): ByteArray {
        repeat(3) { attempt ->
            val response = client.get().uri("/schedule?country={country}&date={date}", country, date)
                .exchange { _, result ->
                    if (result.statusCode.value() == 429) return@exchange null
                    check(result.statusCode.is2xxSuccessful) { "TVmaze returned HTTP ${result.statusCode.value()}" }
                    val body = result.body.readNBytes(MAX_BYTES + 1)
                    check(body.size <= MAX_BYTES) { "TVmaze response exceeds size limit" }
                    body
                }
            if (response != null) return response
            if (attempt < 2) pause(1000L * (attempt + 1))
        }
        error("TVmaze returned HTTP 429 after retries")
    }

    private fun id(node: JsonNode, key: String): Long {
        val value = node.path(key)
        require(value.isIntegralNumber && value.canConvertToLong() && value.asLong() > 0) { "Invalid source ID" }
        return value.asLong()
    }

    private fun field(node: JsonNode, key: String): String? {
        val value = node.path(key)
        if (value.isMissingNode || value.isNull) return null
        require(value.isTextual) { "Invalid $key" }
        return value.asText().replace(Regex("[\\p{Cntrl}]"), " ").take(500)
    }

    companion object {
        private const val MAX_BYTES = 4 * 1024 * 1024
        private fun defaultClient(): RestClient {
            val factory = SimpleClientHttpRequestFactory()
            factory.setConnectTimeout(Duration.ofSeconds(5))
            factory.setReadTimeout(Duration.ofSeconds(15))
            return RestClient.builder().baseUrl("https://api.tvmaze.com")
                .defaultHeader("User-Agent", "PMTD-Agent/1.0").requestFactory(factory).build()
        }
    }
}

data class Episode(val episodeId: Long, val showId: Long, val show: String?, val episode: String?,
                   val airDate: String?, val sourceUrl: String?)

fun runTvmaze(args: Array<String>) {
    require(args.size == 3 && args.all { it.startsWith("--") && it.contains("=") }) {
        "Use --tvmaze-country=US --tvmaze-date=YYYY-MM-DD --tvmaze-limit=20"
    }
    val pairs = args.map { it.substringBefore('=') to it.substringAfter('=') }
    require(pairs.map { it.first }.toSet() == setOf("--tvmaze-country", "--tvmaze-date", "--tvmaze-limit")) {
        "Provide country, date and limit exactly once"
    }
    val options = pairs.toMap()
    val episodes = TvmazeDiscovery().schedule(options.getValue("--tvmaze-country"),
        LocalDate.parse(options.getValue("--tvmaze-date")), options.getValue("--tvmaze-limit").toInt())
    println("TV data supplied by TVmaze (https://www.tvmaze.com/), CC BY-SA.")
    episodes.forEach {
        println("Show ${it.showId}: ${it.show ?: "unknown"} | Episode ${it.episodeId}: ${it.episode ?: "unknown"} | Air date: ${it.airDate ?: "unknown"} | ${it.sourceUrl ?: "source URL unknown"}")
    }
    println("Showing ${episodes.size} episode(s).")
}
