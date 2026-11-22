package dev.pmtd.agent.discovery

import dev.pmtd.agent.catalog.CatalogRepository
import dev.pmtd.agent.catalog.CatalogTitle
import dev.pmtd.agent.catalog.ExternalIdNamespace
import dev.pmtd.agent.catalog.ExternalTitleId
import dev.pmtd.agent.catalog.TitleKind
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class TmdbPreviewTest {
    private val builder = RestClient.builder().baseUrl("https://api.themoviedb.org")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val pauses = mutableListOf<Long>()
    private val discovery = TmdbDiscovery("synthetic-token", builder.build()) { pauses.add(it) }
    private val from = LocalDate.parse("2026-11-01")
    private val to = LocalDate.parse("2026-11-30")
    private val catalog = Mockito.mock(CatalogRepository::class.java)
    private val preview = TmdbPreview(discovery, catalog)

    @Test
    fun `candidate context keeps TMDB votes separate and checks identity`() {
        response("/3/tv/44", """{"id":44,"vote_average":7.2,"vote_count":18,"overview":"Synthetic premise"}""")
        response("/3/tv/45", """{"id":45,"vote_average":0,"vote_count":0}""")
        response("/3/tv/46", """{"id":47,"vote_average":8,"vote_count":100}""")

        val result = discovery.audienceContext(TmdbMedia.TV, 44)
        assertThat(result.score).isEqualByComparingTo("7.2")
        assertThat(result.votes).isEqualTo(18L)
        assertThat(result.synopsis).isEqualTo("Synthetic premise")
        assertThat(discovery.audienceContext(TmdbMedia.TV, 45)).isEqualTo(TmdbAudienceContext(null, null, null))
        assertThatThrownBy { discovery.audienceContext(TmdbMedia.TV, 46) }.hasMessageContaining("details ID")
        server.verify()
    }

    @Test
    fun `movie discovery caps pages and deduplicates candidate IDs`() {
        response(discoverUrl(TmdbMedia.MOVIE, 1), page(1, 3, movie(44), movie(55)))
        response(discoverUrl(TmdbMedia.MOVIE, 2), page(2, 3, movie(55), movie(66)))

        val found = discovery.discover(TmdbMedia.MOVIE, from, to)

        assertThat(found.morePages).isTrue()
        assertThat(found.candidates.map { it.id }).containsExactly(44L, 55L, 66L)
        assertThat(found.candidates.first().date).isEqualTo(LocalDate.parse("2026-11-07"))
        server.verify()
    }

    @Test
    fun `TV preview preserves first air date and origin and caps exact match lookups`() {
        response(discoverUrl(TmdbMedia.MOVIE, 1), page(1, 0))
        val shows = (1..11).map {
            """{"id":$it,"name":"Show $it","first_air_date":"2026-11-10","original_language":"en","origin_country":["GB"],"genre_ids":[18]}"""
        }
        response(discoverUrl(TmdbMedia.TV, 1), page(1, 1, *shows.toTypedArray()))
        for (id in 1..10) response("/3/tv/$id/external_ids", """{"id":$id,"imdb_id":null}""")

        val output = preview.render(from, to)

        assertThat(output).contains("series premiere 2026-11-10", "origin GB", "genre IDs 18",
            "IMDb unmatched", "IMDb not checked (lookup limit)", "not endorsed or certified by TMDB")
        server.verify()
        Mockito.verifyNoInteractions(catalog)
    }

    @Test
    fun `retry honors server delay and stops immediately on unauthorized`() {
        server.expect(requestTo(BASE + discoverUrl(TmdbMedia.MOVIE, 1))).andRespond(
            withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "2"),
        )
        server.expect(requestTo(BASE + discoverUrl(TmdbMedia.MOVIE, 1)))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        assertThatThrownBy { discovery.discover(TmdbMedia.MOVIE, from, to) }
            .hasMessageContaining("HTTP 401").hasMessageNotContaining("synthetic-token")
        assertThat(pauses).containsExactly(2_000L)
        server.verify()
    }

    @Test
    fun `malformed dates and mismatched identity responses fail without echoing provider text`() {
        response(discoverUrl(TmdbMedia.MOVIE, 1), page(1, 1, movie(44, "private-invalid-date")))
        response("/3/movie/44/external_ids", """{"id":45,"imdb_id":"tt123"}""")
        assertThatThrownBy { discovery.discover(TmdbMedia.MOVIE, from, to) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessageNotContaining("private-invalid-date")
        assertThatThrownBy { discovery.imdbId(candidate(TmdbMedia.MOVIE)) }
            .hasMessageContaining("unexpected ID")
        server.verify()
    }

    @Test
    fun `matching uses exact IMDb evidence and distinct TMDB IDs for each media kind`() {
        val movie = title(TitleKind.MOVIE)
        val show = title(TitleKind.SERIES)
        Mockito.`when`(catalog.findByExternalId(ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, "tt100"))).thenReturn(movie)
        Mockito.`when`(catalog.findByExternalId(ExternalTitleId(ExternalIdNamespace.IMDB_TITLE, "tt101"))).thenReturn(show)
        Mockito.`when`(catalog.findByExternalId(ExternalTitleId(ExternalIdNamespace.TMDB_MOVIE, "44"))).thenReturn(movie)
        Mockito.`when`(catalog.findByExternalId(ExternalTitleId(ExternalIdNamespace.TMDB_TV, "44"))).thenReturn(show)
        Mockito.`when`(catalog.findExternalId(movie.id, ExternalIdNamespace.IMDB_TITLE)).thenReturn("tt100")
        Mockito.`when`(catalog.findExternalId(show.id, ExternalIdNamespace.TMDB_TV)).thenReturn("44")

        assertThat(preview.match(candidate(TmdbMedia.MOVIE), "tt100")).isEqualTo(TmdbMatch.MATCHED)
        assertThat(preview.match(candidate(TmdbMedia.TV), "tt101")).isEqualTo(TmdbMatch.MATCHED)
        assertThat(preview.match(candidate(TmdbMedia.TV), "tt100")).isEqualTo(TmdbMatch.CONFLICT)
        assertThat(preview.match(candidate(TmdbMedia.MOVIE), "tt999")).isEqualTo(TmdbMatch.CONFLICT)
        assertThat(preview.match(candidate(TmdbMedia.MOVIE), null)).isEqualTo(TmdbMatch.UNMATCHED)
        assertThat(Mockito.mockingDetails(catalog).invocations.map { it.method.name })
            .containsOnly("findByExternalId", "findExternalId")
    }

    @Test
    fun `import fetch caps identity requests and preserves truncated page metadata`() {
        val movies = (1..11).map { movie(it) }
        response(discoverUrl(TmdbMedia.MOVIE, 1), page(1, 3, *movies.toTypedArray()))
        response(discoverUrl(TmdbMedia.MOVIE, 2), page(2, 3))
        for (id in 1..10) response("/3/movie/$id/external_ids", """{"id":$id,"imdb_id":null}""")
        val batch = fetchTmdbBatch(discovery, TmdbMedia.MOVIE, from, to)
        assertThat(batch.results.pagesFetched).isEqualTo(2)
        assertThat(batch.results.morePages).isTrue()
        assertThat(batch.observations.count { it.checked }).isEqualTo(10)
        assertThat(batch.observations.last().checked).isFalse()
        server.verify()
    }

    @Test
    fun `rated movie lookup uses exact IMDb ID and validates details`() {
        response("/3/find/tt123?external_source=imdb_id", """{"movie_results":[{"id":42}],"tv_results":[],"tv_episode_results":[]}""")
        response("/3/movie/42", """{"id":42,"genres":[{"id":18}],"original_language":"en","origin_country":["US"]}""")
        assertThat(discovery.ratedMetadata("tt123", TmdbMedia.MOVIE))
            .isEqualTo(RatedLookup.Matched(42, listOf(18), "en", listOf("US")))
        server.verify()
    }

    @Test
    fun `rated lookup detects missing and ambiguous identities`() {
        response("/3/find/tt124?external_source=imdb_id", """{"movie_results":[],"tv_results":[],"tv_episode_results":[]}""")
        response("/3/find/tt125?external_source=imdb_id", """{"movie_results":[],"tv_results":[{"id":44},{"id":45}],"tv_episode_results":[]}""")
        response("/3/find/tt126?external_source=imdb_id", """{"movie_results":[],"tv_results":[],"tv_episode_results":[{"id":44}]}""")
        assertThat(discovery.ratedMetadata("tt124", TmdbMedia.MOVIE)).isEqualTo(RatedLookup.Unmatched)
        assertThat(discovery.ratedMetadata("tt125", TmdbMedia.TV)).isEqualTo(RatedLookup.Conflict)
        assertThat(discovery.ratedMetadata("tt126", TmdbMedia.TV)).isEqualTo(RatedLookup.Conflict)
        server.verify()
    }

    @Test
    fun `rated TV lookup retains available genre metadata`() {
        response("/3/find/tt127?external_source=imdb_id", """{"movie_results":[],"tv_results":[{"id":43}],"tv_episode_results":[]}""")
        response("/3/tv/43", """{"id":43,"genres":[{"id":18}],"original_language":"en","origin_country":["GB"]}""")
        assertThat(discovery.ratedMetadata("tt127", TmdbMedia.TV))
            .isEqualTo(RatedLookup.Matched(43, listOf(18), "en", listOf("GB")))
        server.verify()
    }

    @Test
    fun `rated lookup enforces total HTTP request budget even on retry`() {
        val limited = TmdbDiscovery("synthetic-token", builder.build(), requestLimit = 1) { }
        server.expect(requestTo(BASE + "/3/find/tt128?external_source=imdb_id"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))
        assertThatThrownBy { limited.ratedMetadata("tt128", TmdbMedia.TV) }
            .hasMessageContaining("request budget exhausted").hasMessageNotContaining("tt128")
        server.verify()
    }

    @Test
    fun `rated lookup sanitizes HTTP failure and keeps title ID private`() {
        server.expect(requestTo(BASE + "/3/find/tt987654?external_source=imdb_id"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))
        assertThatThrownBy { discovery.ratedMetadata("tt987654", TmdbMedia.TV) }
            .hasMessageNotContaining("tt987654").hasMessageNotContaining("synthetic-token")
        server.verify()
    }

    @Test
    fun `rejects overlong or reversed windows before requests`() {
        assertThatThrownBy { discovery.discover(TmdbMedia.TV, from, to.plusDays(2)) }
            .hasMessageContaining("31 calendar days")
        assertThatThrownBy { discovery.discover(TmdbMedia.TV, to, from) }
            .hasMessageContaining("31 calendar days")
        server.verify()
    }

    private fun response(path: String, json: String) {
        server.expect(requestTo(BASE + path)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-token"))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON))
    }

    private fun discoverUrl(media: TmdbMedia, page: Int) =
        "/3/discover/${media.resource}?with_original_language=en&include_adult=false&" +
            "${media.dateFilter}.gte=$from&${media.dateFilter}.lte=$to&page=$page"

    private fun page(page: Int, total: Int, vararg results: String) =
        """{"page":$page,"total_pages":$total,"results":[${results.joinToString()}]}"""

    private fun movie(id: Int, date: String = "2026-11-07") =
        """{"id":$id,"title":"Synthetic movie","release_date":"$date","original_language":"en"}"""

    private fun candidate(media: TmdbMedia) = TmdbCandidate(media, 44, "Synthetic", from, "en", emptyList(), emptyList())

    private fun title(kind: TitleKind) = CatalogTitle(UUID.randomUUID(), "Synthetic", kind, null, Instant.EPOCH, Instant.EPOCH)

    companion object {
        private const val BASE = "https://api.themoviedb.org"
    }
}
