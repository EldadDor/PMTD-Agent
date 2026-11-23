package dev.pmtd.agent.notification

import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class LineWebhookControllerTest {
    private val secret = "synthetic-webhook-secret"
    private val bot = "U" + "0".repeat(32)
    private val user = "U" + "1".repeat(32)
    private val outsider = "U" + "2".repeat(32)
    private val item = UUID.fromString("3c210f9d-ff0f-4081-af14-89aafd23f095")
    private val eventId = "01ARZ3NDEKTSV4RRFFQ69G5FAV"
    private val time = 1_795_387_200_000L
    private val received = mutableListOf<LinePostbackEvent>()
    private val mvc = MockMvcBuilders.standaloneSetup(
        LineWebhookController(secret, bot, user, LineFeedbackHandler { received.add(it) }),
    ).build()

    @Test
    fun `accepts correctly signed raw UTF-8 and dispatches a title-specific action`() {
        val raw = envelope(event())
        assertThat(deliver(raw)).isEqualTo(200)
        assertThat(received).containsExactly(LinePostbackEvent(eventId, item, "watched", time))
    }

    @Test
    fun `rejects missing malformed and mismatched signatures without dispatch`() {
        val raw = envelope(event())
        assertThat(deliver(raw, null)).isEqualTo(401)
        assertThat(deliver(raw, "%%%")).isEqualTo(401)
        assertThat(deliver(raw, sign(raw, "different-secret"))).isEqualTo(401)
        assertThat(deliver(raw.trim(), sign(raw))).isEqualTo(401)
        assertThat(received).isEmpty()
    }

    @Test
    fun `accepts signed verification and rejects signed malformed JSON without dispatch`() {
        assertThat(deliver(envelope())).isEqualTo(200)
        assertThat(deliver("{")).isEqualTo(400)
        assertThat(received).isEmpty()
    }

    @Test
    fun `rejects a correctly signed callback above the body limit`() {
        val oversized = envelope(event()) + " ".repeat(1_048_576)
        assertThat(deliver(oversized)).isEqualTo(401)
        assertThat(received).isEmpty()
    }

    @Test
    fun `ignores wrong destination and unauthorized source without dispatch`() {
        assertThat(deliver(envelope(event(), destination = outsider))).isEqualTo(400)
        assertThat(deliver(envelope(event(sourceUser = outsider)))).isEqualTo(200)
        assertThat(deliver(envelope(event(sourceType = "group")))).isEqualTo(200)
        assertThat(deliver(envelope(event(sourceType = "room")))).isEqualTo(200)
        assertThat(received).isEmpty()
    }

    @Test
    fun `ignores malformed and unsupported postbacks`() {
        for (data in listOf(
            "item=invalid-uuid&action=watched",
            "item=$item&action=unknown",
            "item=$item&action=watched&action=interested",
            "action=watched",
        )) {
            deliver(envelope(event(data = data)))
        }
        deliver(envelope(event(type = "message")))
        assertThat(received).isEmpty()
    }

    private fun envelope(events: String = "", destination: String = bot): String = """
        {
            "destination": "$destination",
            "events": [$events]
        }
    """.trimIndent() + "\n"

    private fun event(
        sourceUser: String = user,
        sourceType: String = "user",
        data: String = "item=$item&action=watched",
        type: String = "postback",
    ): String = """
        {
            "type": "$type",
            "timestamp": $time,
            "webhookEventId": "$eventId",
            "source": { "type": "$sourceType", "userId": "$sourceUser" },
            "postback": { "data": "$data" },
            "ignored": "réception 🎬"
        }
    """.trimIndent()

    private fun deliver(body: String, signature: String? = sign(body)): Int {
        val request = post("/webhook/line").contentType(MediaType.APPLICATION_JSON)
            .content(body.toByteArray(Charsets.UTF_8))
        if (signature != null) request.header("X-Line-Signature", signature)
        return mvc.perform(request).andReturn().response.status
    }

    private fun sign(body: String, key: String = secret): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(body.toByteArray(Charsets.UTF_8)))
    }
}
