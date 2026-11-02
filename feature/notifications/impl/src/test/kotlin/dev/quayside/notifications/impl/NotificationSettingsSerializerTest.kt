package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.PopupDuration
import dev.quayside.notifications.api.PopupPosition
import dev.quayside.notifications.api.PopupSettings
import dev.quayside.notifications.api.PopupStyle
import dev.quayside.notifications.api.SpeechSettings
import dev.quayside.notifications.api.VoiceId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/** `data` of `notifications.json`: round trip, defaults for missing keys and rejection of wrong types. */
class NotificationSettingsSerializerTest {
    private fun decode(data: String): NotificationSettings = Json.decodeFromString(NotificationSettingsSerializer, data)

    @Test
    fun `settings survive a round trip`() {
        val settings =
            NotificationSettings(popups = false, runningLongMinutes = 45)
                .withChannel(NotificationKind.TURN_ENDED, NotificationChannel.SOUND, true)
                .withChannel(NotificationKind.FAILED, NotificationChannel.VOICE, false)

        val text = Json.encodeToString(NotificationSettingsSerializer, settings)

        decode(text).let {
            it.popups shouldBe false
            it.runningLongMinutes shouldBe 45
            NotificationKind.entries.forEach { kind -> it.channelsFor(kind) shouldBe settings.channelsFor(kind) }
        }
    }

    @Test
    fun `popup settings survive a round trip`() {
        val popup = PopupSettings(PopupPosition.TOP_LEFT, PopupDuration.ALWAYS_STAY, 5, PopupStyle.COMPACT)

        val text = Json.encodeToString(NotificationSettingsSerializer, NotificationSettings(popup = popup))

        decode(text).popup shouldBe popup
    }

    @Test
    fun `unknown popup values fall back to the defaults and the count is clamped`() {
        val settings =
            decode("""{"popup": {"position": "moon", "duration": "x", "style": "y", "maxVisible": 99}}""")

        settings.popup shouldBe PopupSettings(maxVisible = PopupSettings.MAX_VISIBLE_LIMIT)
    }

    @Test
    fun `a popup entry of the wrong type is rejected`() {
        shouldThrow<SerializationException> { decode("""{"popup": []}""") }
        shouldThrow<SerializationException> { decode("""{"popup": {"position": 1}}""") }
    }

    @Test
    fun `an empty object decodes to the defaults`() {
        decode("{}") shouldBe NotificationSettings()
    }

    @Test
    fun `unknown keys kinds and channels are skipped`() {
        val settings =
            decode(
                """{"future": 1, "channels": {"newKind": ["popup"], "failed": ["popup", "hologram"]}}""",
            )

        settings.channelsFor(NotificationKind.FAILED) shouldBe setOf(NotificationChannel.POPUP)
    }

    @Test
    fun `an empty list mutes the kind`() {
        decode("""{"channels": {"turnEnded": []}}""").channelsFor(NotificationKind.TURN_ENDED) shouldBe emptySet()
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -5, 100000])
    fun `a threshold out of range is clamped`(minutes: Int) {
        val expected =
            minutes.coerceIn(
                NotificationSettings.MIN_RUNNING_LONG_MINUTES,
                NotificationSettings.MAX_RUNNING_LONG_MINUTES,
            )

        decode("""{"runningLongMinutes": $minutes}""").runningLongMinutes shouldBe expected
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "[]",
            """{"popups": "yes"}""",
            """{"voice": 1}""",
            """{"runningLongMinutes": "30"}""",
            """{"runningLongMinutes": 2.5}""",
            """{"channels": []}""",
            """{"channels": {"failed": "popup"}}""",
            """{"channels": {"failed": [1]}}""",
        ],
    )
    fun `a value of the wrong type is rejected without quoting it`(data: String) {
        val error = shouldThrow<SerializationException> { decode(data) }

        error.message.orEmpty() shouldNotContain "yes"
    }

    @Test
    fun `round trips the speech section`() {
        val speech =
            SpeechSettings(
                voice = VoiceId("Ewa"),
                rate = 250,
                volume = 40,
                templates = mapOf(NotificationKind.NEEDS_INPUT to "{sesja} czeka", NotificationKind.FAILED to "Ups"),
                readQuestion = false,
                onlyInBackground = false,
                muteWhenMicrophoneActive = false,
            )

        val text = Json.encodeToString(NotificationSettingsSerializer, NotificationSettings(speech = speech))

        decode(text).speech shouldBe speech
    }

    @Test
    fun `the speech section uses the wire names`() {
        val speech = SpeechSettings(templates = mapOf(NotificationKind.NEEDS_INPUT to "{sesja} czeka"))

        val text = Json.encodeToString(NotificationSettingsSerializer, NotificationSettings(speech = speech))

        text shouldContain """"speech""""
        text shouldContain """"needsInput":"{sesja} czeka""""
    }

    @Test
    fun `missing speech section gives defaults`() {
        decode("""{"popups": false}""").speech shouldBe SpeechSettings()
        decode("""{"speech": {}}""").speech shouldBe SpeechSettings()
    }

    @Test
    fun `null voice means the system voice`() {
        decode("""{"speech": {"voice": null}}""").speech.voice shouldBe null

        val text =
            Json.encodeToString(
                NotificationSettingsSerializer,
                NotificationSettings(speech = SpeechSettings(voice = null)),
            )

        decode(text).speech.voice shouldBe null
    }

    @Test
    fun `an empty voice gives the default voice`() {
        decode("""{"speech": {"voice": ""}}""").speech.voice shouldBe SpeechSettings.DEFAULT_VOICE
    }

    @ParameterizedTest(name = "rate {0} gives {1}")
    @CsvSource("5,90", "89,90", "90,90", "360,360", "361,360", "100000,360", "-3,90")
    fun `clamps rate`(
        rate: Int,
        expected: Int,
    ) {
        decode("""{"speech": {"rate": $rate}}""").speech.rate shouldBe expected
    }

    @ParameterizedTest(name = "volume {0} gives {1}")
    @CsvSource("-5,0", "0,0", "100,100", "101,100", "5000,100")
    fun `clamps volume`(
        volume: Int,
        expected: Int,
    ) {
        decode("""{"speech": {"volume": $volume}}""").speech.volume shouldBe expected
    }

    @Test
    fun `blank template uses the default`() {
        val speech = decode("""{"speech": {"templates": {"needsInput": "  ", "failed": ""}}}""").speech

        speech.templates shouldBe emptyMap()
        speech.templateFor(NotificationKind.NEEDS_INPUT) shouldBe
            SpeechSettings.defaultTemplate(NotificationKind.NEEDS_INPUT)
    }

    @Test
    fun `unknown template kind is skipped`() {
        val speech = decode("""{"speech": {"templates": {"newKind": "x", "failed": "Ups"}}}""").speech

        speech.templates shouldBe mapOf(NotificationKind.FAILED to "Ups")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"speech": []}""",
            """{"speech": {"rate": "fast"}}""",
            """{"speech": {"rate": 1.5}}""",
            """{"speech": {"volume": "loud"}}""",
            """{"speech": {"voice": 5}}""",
            """{"speech": {"readQuestion": "yes"}}""",
            """{"speech": {"onlyInBackground": 1}}""",
            """{"speech": {"muteWhenMicrophoneActive": "no"}}""",
            """{"speech": {"templates": []}}""",
            """{"speech": {"templates": {"failed": 7}}}""",
        ],
    )
    fun `wrong type of a speech value throws`(data: String) {
        shouldThrow<SerializationException> { decode(data) }
    }
}
