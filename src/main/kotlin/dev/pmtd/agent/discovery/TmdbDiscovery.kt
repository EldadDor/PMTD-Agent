package dev.pmtd.agent.discovery

import dev.pmtd.agent.catalog.ExternalIdNamespace
import dev.pmtd.agent.catalog.TitleKind
import java.net.HttpURLConnection
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

internal enum class TmdbMedia(
    val resource: String,
    val titleField: String,
    val dateField: String,
    val dateFilter: String,
    val dateLabel: String,
    val namespace: ExternalIdNamespace,
    val kind: TitleKind,
) {
    MOVIE("movie", "title", "release_date", "primary_release_date", "primary release",
        ExternalIdNamespace.TMDB_MOVIE, TitleKind.MOVIE),
    TV("tv", "name", "first_air_date", "first_air_date", "series premiere",
        ExternalIdNamespace.TMDB_TV, TitleKind.SERIES),
}

internal data class TmdbCandidate(
    val media: TmdbMedia,
    val id: Long,
    val title: String,
    val date: LocalDate?,
    val originalLanguage: String?,
    val genreIds: List<Long>,
    val originCountries: List<String>,
)

internal data class TmdbResults(val candidates: List<TmdbCandidate>, val morePages: Boolean, val pagesFetched: Int)

internal class TmdbDiscovery(
    token: String,
    private val client: RestClient = defaultClient(),
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
) {
    init {
        require(token.matches(Regex("[A-Za-z0-9._~-]+"))) { "Set a valid PMTD_TMDB_READ_TOKEN locally" }
    }

    private val readToken = token
    private val mapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build()
    private val startedAt = System.nanoTime()

    fun discover(media: TmdbMedia, from: LocalDate, to: LocalDate): TmdbResults {
        validateTmdbWindow(from, to)
        val candidates = linkedMapOf<Long, TmdbCandidate>()
        var totalPages = 1
        var pagesFetched = 0
        for (page in 1..PAGE_LIMIT) {
            if (page > totalPages) break
            val root = request("/3/discover/${media.resource}") { request ->
                request.uri { builder ->
                    builder.path("/3/discover/${media.resource}")
                        .queryParam("with_original_language", "en")
                        .queryParam("include_adult", false)
                        .queryParam("${media.dateFilter}.gte", from)
                        .queryParam("${media.dateFilter}.lte", to)
                        .queryParam("page", page)
                        .build()
                }
            }
            require(root.path("page").isIntegralNumber && root.path("page").asLong() == page.toLong()) {
                "Unexpected TMDB page"
            }
            pagesFetched++
            val pages = root.path("total_pages")
            require(pages.isIntegralNumber && pages.canConvertToInt() && pages.asInt() >= 0) {
                "Invalid TMDB page count"
            }
            totalPages = pages.asInt()
            val results = root.path("results")
            require(results.isArray && results.size() <= 20) { "Invalid TMDB result page" }
            require((totalPages == 0 && page == 1 && results.isEmpty) || totalPages >= page) {
                "Inconsistent TMDB page count"
            }
            for (node in results) {
                require(node.isObject) { "Invalid TMDB candidate" }
                val id = positiveId(node.path("id"))
                val title = optionalText(node, media.titleField)?.replace(CONTROL, " ")?.trim()?.take(500)
                require(!title.isNullOrBlank()) { "Missing TMDB title" }
                val date = optionalText(node, media.dateField)?.takeIf { it.isNotEmpty() }?.let(::parseTmdbDate)
                val language = optionalText(node, "original_language")
                require(language == null || language.matches(Regex("[a-z]{2}"))) { "Invalid TMDB original language" }
                val genres = optionalArray(node, "genre_ids").map(::positiveId)
                val countries = optionalArray(node, "origin_country").map { value ->
                    require(value.isString && value.stringValue().matches(Regex("[A-Z]{2}"))) {
                        "Invalid TMDB origin country"
                    }
                    value.stringValue()
                }
                if (language != null && language != "en" || date != null && date !in from..to) continue
                candidates.putIfAbsent(id, TmdbCandidate(media, id, title, date, language, genres, countries))
            }
        }
        return TmdbResults(candidates.values.toList(), totalPages > PAGE_LIMIT, pagesFetched)
    }

    fun imdbId(candidate: TmdbCandidate): String? {
        val path = "/3/${candidate.media.resource}/${candidate.id}/external_ids"
        val root = request(path) { it.uri(path) }
        require(positiveId(root.path("id")) == candidate.id) { "TMDB external IDs response has an unexpected ID" }
        val imdbId = optionalText(root, "imdb_id")?.takeIf { it.isNotEmpty() } ?: return null
        require(imdbId.matches(Regex("tt[0-9]+"))) { "Invalid IMDb identity reported by TMDB" }
        return imdbId
    }

    private fun request(
        endpoint: String,
        uri: (RestClient.RequestHeadersUriSpec<*>) -> RestClient.RequestHeadersSpec<*>,
    ): JsonNode {
        for (attempt in 0..2) {
            check(remainingMillis() > 0) { "TMDB preview time limit reached" }
            var retryDelay: Long? = null
            val body = try {
                uri(client.get()).headers { it.setBearerAuth(readToken) }.accept(MediaType.APPLICATION_JSON)
                    .exchange { _, response ->
                        val status = response.statusCode.value()
                        if (status == 429 || status in 500..599) {
                            check(attempt < 2) { "TMDB HTTP $status after retries for $endpoint" }
                            retryDelay = retryAfterMillis(response.headers.getFirst(HttpHeaders.RETRY_AFTER))
                                ?: (1000L shl attempt)
                            return@exchange null
                        }
                        check(status in 200..299) { "TMDB HTTP $status for $endpoint" }
                        response.body.readNBytes(RESPONSE_LIMIT + 1)
                    }
            } catch (_: RestClientException) {
                error("TMDB request failed for $endpoint")
            }
            if (body != null) {
                check(remainingMillis() > 0) { "TMDB preview time limit reached" }
                check(body.size in 1..RESPONSE_LIMIT) { "Invalid TMDB response size" }
                val root = try {
                    mapper.readTree(body)
                } catch (_: JacksonException) {
                    error("Invalid TMDB JSON response")
                }
                require(root.isObject) { "Invalid TMDB response object" }
                return root
            }
            val delay = checkNotNull(retryDelay) { "Empty TMDB response" }
            check(delay <= 5_000 && delay < remainingMillis()) { "TMDB requested a retry beyond the preview budget" }
            pause(delay)
        }
        error("TMDB retry limit reached")
    }

    private fun remainingMillis(): Long = 90_000 - (System.nanoTime() - startedAt) / 1_000_000

    private fun retryAfterMillis(value: String?): Long? {
        val header = value?.trim() ?: return null
        if (header.matches(Regex("[0-9]+"))) {
            return (header.toLongOrNull() ?: Long.MAX_VALUE).coerceAtMost(6) * 1_000
        }
        return try {
            val target = ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME)
            Duration.between(ZonedDateTime.now(), target).toMillis().coerceAtLeast(0)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun optionalText(node: JsonNode, field: String): String? {
        val value = node.path(field)
        if (value.isMissingNode || value.isNull) return null
        require(value.isString) { "Invalid TMDB $field" }
        return value.stringValue()
    }

    private fun optionalArray(node: JsonNode, field: String): List<JsonNode> {
        val value = node.path(field)
        if (value.isMissingNode || value.isNull) return emptyList()
        require(value.isArray) { "Invalid TMDB $field" }
        return value.toList()
    }

    private fun positiveId(value: JsonNode): Long {
        require(value.isIntegralNumber && value.canConvertToLong() && value.asLong() > 0) { "Invalid TMDB ID" }
        return value.asLong()
    }

    companion object {
        private const val PAGE_LIMIT = 2
        private const val RESPONSE_LIMIT = 1024 * 1024
        private val CONTROL = Regex("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]")

        private fun defaultClient(): RestClient {
            val factory = object : SimpleClientHttpRequestFactory() {
                override fun prepareConnection(connection: HttpURLConnection, httpMethod: String) {
                    super.prepareConnection(connection, httpMethod)
                    connection.instanceFollowRedirects = false
                }
            }
            factory.setConnectTimeout(Duration.ofSeconds(3))
            factory.setReadTimeout(Duration.ofSeconds(5))
            return RestClient.builder().baseUrl("https://api.themoviedb.org")
                .requestFactory(factory).build()
        }
    }
}

internal fun parseTmdbDate(value: String): LocalDate {
    require(value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) { "Expected a date in YYYY-MM-DD format" }
    return try {
        LocalDate.parse(value).also { require(it.year > 0) { "Expected a positive calendar year" } }
    } catch (_: DateTimeParseException) {
        throw IllegalArgumentException("Invalid calendar date")
    }
}

internal fun validateTmdbWindow(from: LocalDate, to: LocalDate) {
    require(ChronoUnit.DAYS.between(from, to) in 0..30) { "Use a date window of 1 to 31 calendar days" }
}
