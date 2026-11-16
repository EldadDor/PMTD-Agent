package dev.pmtd.agent

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.json.JsonParserFactory

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationHealthTest {
    @LocalServerPort
    private var port: Int = 0

    private val client = HttpClient.newHttpClient()

    @Test
    fun `health endpoint reports up without internal details`() {
        val response = get("/actuator/health")

        assertThat(response.statusCode()).isEqualTo(200)
        val health = JsonParserFactory.getJsonParser().parseMap(response.body())
        assertThat(health).containsEntry("status", "UP")
            .doesNotContainKeys("components", "details")
    }

    @Test
    fun `sensitive actuator endpoints are not exposed`() {
        listOf("env", "configprops", "beans", "loggers", "heapdump").forEach { endpoint ->
            assertThat(get("/actuator/$endpoint").statusCode())
                .describedAs("Actuator endpoint %s", endpoint)
                .isEqualTo(404)
        }
    }

    private fun get(path: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path")).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )
}
