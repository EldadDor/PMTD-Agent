package dev.pmtd.agent.notification

import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.HttpServletRequest
import tools.jackson.databind.json.JsonMapper

internal data class LinePostbackEvent(val id: String, val itemId: UUID, val action: String, val timestamp: Long)
internal fun interface LineFeedbackHandler { fun accept(event: LinePostbackEvent) }

@RestController
@Profile("line-webhook & postgres")
internal class LineWebhookController(
    @Value("\${PMTD_LINE_CHANNEL_SECRET}") private val secret: String,
    @Value("\${PMTD_LINE_BOT_ID}") private val destination: String,
    @Value("\${PMTD_LINE_USER_ID}") private val allowedUser: String,
    private val handler: LineFeedbackHandler,
) {
    private val mapper = JsonMapper.builder().build()

    @PostMapping("/webhook/line")
    fun receive(request: HttpServletRequest, @RequestHeader("X-Line-Signature", required = false) signature: String?): ResponseEntity<Void> {
        if (secret.isBlank() || destination.isBlank() || allowedUser.isBlank()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        val body = request.inputStream.readNBytes(1_048_577)
        if (body.size > 1_048_576 || signature == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val provided = try { Base64.getDecoder().decode(signature) } catch (_: IllegalArgumentException) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        if (!MessageDigest.isEqual(mac.doFinal(body), provided)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val root = try { mapper.readTree(body) } catch (_: Exception) { return ResponseEntity.badRequest().build() }
        if (!root.isObject || root.path("destination").asText() != destination || !root.path("events").isArray) {
            return ResponseEntity.badRequest().build()
        }
        for (event in root.path("events")) {
            if (event.path("type").asText() != "postback" || event.path("source").path("type").asText() != "user" ||
                event.path("source").path("userId").asText() != allowedUser) continue
            val parts = event.path("postback").path("data").asText().split("&")
            if (parts.size != 2 || !parts[0].startsWith("item=") || !parts[1].startsWith("action=")) continue
            val item = try { UUID.fromString(parts[0].substringAfter("=")) } catch (_: IllegalArgumentException) { continue }
            val action = parts[1].substringAfter("=")
            val id = event.path("webhookEventId").asText()
            val timestamp = event.path("timestamp")
            if (action !in setOf("interested", "not_interested", "watched") || id.isBlank() ||
                !timestamp.isIntegralNumber || !timestamp.canConvertToLong() || timestamp.asLong() <= 0) continue
            handler.accept(LinePostbackEvent(id, item, action, timestamp.asLong()))
        }
        return ResponseEntity.ok().build()
    }
}
