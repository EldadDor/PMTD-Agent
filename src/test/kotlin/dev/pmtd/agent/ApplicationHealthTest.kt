package dev.pmtd.agent

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.json.JsonParserFactory
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationHealthTest {
    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var environment: Environment

    @LocalServerPort
    private var port: Int = 0

    private val client = HttpClient.newHttpClient()

    @Test
    fun `default startup has no database or migrations`() {
        assertThat(context.getBeansOfType(DataSource::class.java)).isEmpty()
        assertThat(context.getBeansOfType(Flyway::class.java)).isEmpty()
    }

    @Test
    fun `startup leaves AI disabled without contacting Ollama`() {
        assertThat(context.getBeansOfType(ChatModel::class.java)).isEmpty()
        assertThat(context.getBeansOfType(EmbeddingModel::class.java)).isEmpty()
        assertThat(environment.getProperty("spring.ai.ollama.init.pull-model-strategy"))
            .isEqualTo("never")
        assertThat(ollamaRequests.get()).isZero()
    }

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

    companion object {
        private val ollamaRequests = AtomicInteger()
        private val fakeOllama = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                ollamaRequests.incrementAndGet()
                exchange.sendResponseHeaders(503, -1)
                exchange.close()
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun ollamaEndpoint(registry: DynamicPropertyRegistry) {
            registry.add("spring.ai.ollama.base-url") { "http://127.0.0.1:${fakeOllama.address.port}" }
        }

        @JvmStatic
        @AfterAll
        fun stopFakeOllama() {
            fakeOllama.stop(0)
        }
    }
}
