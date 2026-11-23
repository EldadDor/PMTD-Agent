package dev.pmtd.agent.notification

import java.util.UUID
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper

internal enum class LinePushResult { ACCEPTED, REJECTED, UNCERTAIN }

internal class LineGateway(
    private val token: String,
    private val recipient: String,
    private val client: RestClient,
) {
    private val mapper = JsonMapper.builder().build()

    fun push(message: String, retryKey: UUID): LinePushResult {
        val messages = mapper.readTree(message)
        require(messages.isArray && !messages.isEmpty && messages.size() <= 5) { "Invalid prepared message" }
        val body = mapper.writeValueAsString(mapOf("to" to recipient, "messages" to messages))
        return try {
            client.post().uri("/v2/bot/message/push")
                .headers { it.setBearerAuth(token); it.set("X-Line-Retry-Key", retryKey.toString()) }
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange { _, response ->
                    when (response.statusCode.value()) {
                        in 200..299 -> LinePushResult.ACCEPTED
                        409 -> if (response.headers.getFirst("x-line-accepted-request-id").isNullOrBlank())
                            LinePushResult.UNCERTAIN else LinePushResult.ACCEPTED
                        in 400..499 -> LinePushResult.REJECTED
                        else -> LinePushResult.UNCERTAIN
                    }
                } ?: LinePushResult.UNCERTAIN
        } catch (_: Exception) {
            LinePushResult.UNCERTAIN
        }
    }
}
