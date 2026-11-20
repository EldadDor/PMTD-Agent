package dev.pmtd.agent.discovery

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.LocalDate

class TvmazeDiscoveryTest {
    private val builder = RestClient.builder().baseUrl("https://api.tvmaze.com")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = TvmazeDiscovery(builder.build()) { }
    private val date = LocalDate.parse("2026-11-20")
    private val url = "https://api.tvmaze.com/schedule?country=US&date=2026-11-20"

    @Test
    fun `normalizes missing fields and caps results`() {
        server.expect(requestTo(url)).andRespond(withSuccess(
            """[{"id":1,"name":"Pilot","airdate":"2026-11-20","show":{"id":2,"name":"Example","url":"https://www.tvmaze.com/shows/2/example"}},{"id":3,"show":{"id":4}}]""",
            MediaType.APPLICATION_JSON))
        val result = client.schedule("US", date, 1)
        assertEquals(1, result.size)
        assertEquals("Example", result[0].show)
        server.verify()
    }

    @Test
    fun `accepts unknown names and dates`() {
        server.expect(requestTo(url)).andRespond(withSuccess(
            """[{"id":3,"show":{"id":4}}]""", MediaType.APPLICATION_JSON))
        val item = client.schedule("US", date, 10).single()
        assertNull(item.show)
        assertNull(item.airDate)
    }

    @Test
    fun `rejects malformed entries before returning any`() {
        server.expect(requestTo(url)).andRespond(withSuccess(
            """[{"id":1,"show":{"id":2}},{"id":3}]""", MediaType.APPLICATION_JSON))
        assertThrows(IllegalArgumentException::class.java) { client.schedule("US", date, 1) }
    }

    @Test
    fun `reports rate limit and service outage`() {
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
        assertThrows(IllegalStateException::class.java) { client.schedule("US", date, 1) }
    }

    @Test
    fun `fetch retains all episodes beyond dry run limit`() {
        val body = (1..101).joinToString(",", "[", "]") { "{\"id\":$it,\"show\":{\"id\":1}}" }
        server.expect(requestTo(url)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
        assertEquals(101, client.fetch("US", date).size)
    }

    @Test
    fun `retries rate limit then returns complete empty schedule`() {
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))
        server.expect(requestTo(url)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))
        assertTrue(client.fetch("US", date).isEmpty())
        server.verify()
    }

    @Test
    fun `stops after bounded rate limit retries`() {
        repeat(3) { server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)) }
        assertThrows(IllegalStateException::class.java) { client.fetch("US", date) }
        server.verify()
    }

    @Test
    fun `rejects invalid inputs before requesting`() {
        assertThrows(IllegalArgumentException::class.java) { client.schedule("us", date, 1) }
        assertThrows(IllegalArgumentException::class.java) { client.schedule("US", date, 101) }
    }
}
