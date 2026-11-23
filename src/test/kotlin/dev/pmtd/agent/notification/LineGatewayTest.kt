package dev.pmtd.agent.notification

import java.net.SocketTimeoutException
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders.AUTHORIZATION
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class LineGatewayTest {
    private val baseUrl = "https://api.line.example"
    private val recipient = "U" + "1".repeat(32)
    private val token = "synthetic-token"
    private val retryKey = UUID.fromString("3c210f9d-ff0f-4081-af14-89aafd23f095")
    private val messages = """[{"type":"text","text":"Synthetic recommendation"}]"""
    private val builder = RestClient.builder().baseUrl(baseUrl)
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val gateway = LineGateway(token, recipient, builder.build())

    @Test
    fun `push sends the prepared content with configured target and retry key`() {
        expectedPush().andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        assertThat(gateway.push(messages, retryKey)).isEqualTo(LinePushResult.ACCEPTED)
        server.verify()
    }

    @Test
    fun `accepted retry outcome uses the same delivery key and payload`() {
        expectedPush().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
        expectedPush().andRespond(withStatus(HttpStatus.CONFLICT).header("x-line-accepted-request-id", "synthetic-request"))
        assertThat(gateway.push(messages, retryKey)).isEqualTo(LinePushResult.UNCERTAIN)
        assertThat(gateway.push(messages, retryKey)).isEqualTo(LinePushResult.ACCEPTED)
        server.verify()
    }

    @Test
    fun `provider rejection is distinct from an uncertain connection timeout`() {
        expectedPush().andRespond(withStatus(HttpStatus.UNAUTHORIZED))
        expectedPush().andRespond(withException(SocketTimeoutException("synthetic timeout")))
        assertThat(gateway.push(messages, retryKey)).isEqualTo(LinePushResult.REJECTED)
        assertThat(gateway.push(messages, retryKey)).isEqualTo(LinePushResult.UNCERTAIN)
        server.verify()
    }

    @Test
    fun `a conflict without an accepted request ID is uncertain`() {
        expectedPush().andRespond(withStatus(HttpStatus.CONFLICT))
        assertThat(gateway.push(messages, retryKey)).isEqualTo(LinePushResult.UNCERTAIN)
        server.verify()
    }

    private fun expectedPush() = server.expect(requestTo("$baseUrl/v2/bot/message/push"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(AUTHORIZATION, "Bearer $token"))
        .andExpect(header("X-Line-Retry-Key", retryKey.toString()))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().json("""{"to":"$recipient","messages":$messages}"""))
}
