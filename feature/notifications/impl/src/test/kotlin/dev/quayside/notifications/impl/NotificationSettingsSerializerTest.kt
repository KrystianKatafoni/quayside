package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.PopupDuration
import dev.quayside.notifications.api.PopupPosition
import dev.quayside.notifications.api.PopupSettings
import dev.quayside.notifications.api.PopupStyle
import dev.quayside.notifications.api.QuietHours
import dev.quayside.notifications.api.SoundId
import dev.quayside.notifications.api.SoundSettings
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
import java.time.LocalTime

/** `data` of `notifications.json`: round trip, defaults for missing keys and rejection of wrong types. */
class NotificationSettingsSerializerTest {
    private fun decode(data: String): NotificationSettings = Json.decodeFromString(NotificationSettingsSerializer, data)

    @Test
    fun `settings survive a round trip`() {
        val settings =
            NotificationSettings(popups = false, runningLongMinutes = 45)
                .withChannel(NotificationKind.TURN_ENDED, NotificationChannel.VOICE, true)
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
    fun `a file with deviations of other kinds keeps the default of a finished turn`() {
        val settings = decode("""{"channels": {"failed": ["popup"], "needsInput": []}}""")

        settings.channelsFor(NotificationKind.TURN_ENDED) shouldBe
            setOf(NotificationChannel.POPUP, NotificationChannel.SOUND)
        settings.channelsFor(NotificationKind.FAILED) shouldBe setOf(NotificationChannel.POPUP)
    }

    @Test
    fun `a file without channels gives a finished turn the popup and the sound`() {
        decode("{}").channelsFor(NotificationKind.TURN_ENDED) shouldBe
            setOf(NotificationChannel.POPUP, NotificationChannel.SOUND)
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

    @Test
    fun `round trips quiet hours`() {
        val quiet = QuietHours(true, LocalTime.of(21, 30), LocalTime.of(6, 15), respectFocus = false)

        val text = Json.encodeToString(NotificationSettingsSerializer, NotificationSettings(quietHours = quiet))

        decode(text).quietHours shouldBe quiet
        text shouldContain """"quietHours""""
        text shouldContain """"from":"21:30""""
        text shouldContain """"to":"06:15""""
    }

    @Test
    fun `missing quiet hours gives defaults`() {
        decode("""{"popups": false}""").quietHours shouldBe QuietHours()
        decode("""{"quietHours": {}}""").quietHours shouldBe QuietHours()
    }

    @Test
    fun `a partial quiet hours section keeps the other defaults`() {
        decode("""{"quietHours": {"enabled": true}}""").quietHours shouldBe QuietHours(enabled = true)
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = ["24:00", "07:60", "7", "abc", "", "07:00:30", "-1:00", "07:0"])
    fun `bad time text gives the default`(text: String) {
        val quiet = decode("""{"quietHours": {"enabled": true, "from": "$text", "to": "$text"}}""").quietHours

        quiet shouldBe QuietHours(enabled = true)
    }

    @Test
    fun `one bad time keeps the other`() {
        decode("""{"quietHours": {"from": "abc", "to": "06:00"}}""").quietHours shouldBe
            QuietHours(to = LocalTime.of(6, 0))
        decode("""{"quietHours": {"from": "21:00", "to": "25:00"}}""").quietHours shouldBe
            QuietHours(from = LocalTime.of(21, 0))
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            """{"quietHours": []}""",
            """{"quietHours": {"enabled": "yes"}}""",
            """{"quietHours": {"enabled": 1}}""",
            """{"quietHours": {"enabled": "true"}}""",
            """{"quietHours": {"respectFocus": "no"}}""",
            """{"quietHours": {"respectFocus": "false"}}""",
            """{"quietHours": {"from": 7}}""",
            """{"quietHours": {"from": null}}""",
            """{"quietHours": {"to": true}}""",
            """{"quietHours": {"to": null}}""",
        ],
    )
    fun `wrong type in quiet hours throws`(data: String) {
        shouldThrow<SerializationException> { decode(data) }
    }

    @Test
    fun `the error of a wrong quiet hours value does not quote it`() {
        val error = shouldThrow<SerializationException> { decode("""{"quietHours": {"enabled": "yes"}}""") }

        error.message.orEmpty() shouldNotContain "yes"
    }

    @Test
    fun `default quiet hours are not written`() {
        val text = Json.encodeToString(NotificationSettingsSerializer, NotificationSettings())

        text shouldNotContain "quietHours"
    }

    @Test
    fun `non default quiet hours are written`() {
        val text =
            Json.encodeToString(
                NotificationSettingsSerializer,
                NotificationSettings(quietHours = QuietHours(enabled = true)),
            )

        text shouldContain """"quietHours""""
    }

    @Test
    fun `default sound settings write no sound section`() {
        val text = Json.encodeToString(NotificationSettingsSerializer, NotificationSettings())

        text.contains("\"sound\"") shouldBe false
    }

    @Test
    fun `sound settings write only the deviations and round trip`() {
        val sound = SoundSettings(volume = 40, sounds = mapOf(NotificationKind.FAILED to SoundId("Funk")))
        val text = Json.encodeToString(NotificationSettingsSerializer, NotificationSettings(sound = sound))

        text.contains("\"failed\":\"Funk\"") shouldBe true
        text.contains("needsInput\":\"") shouldBe false
        decode(text).sound shouldBe sound
    }

    @Test
    fun `a sound equal to its default is not written`() {
        val sound = SoundSettings(sounds = mapOf(NotificationKind.FAILED to SoundId("Basso")))

        Json
            .encodeToString(
                NotificationSettingsSerializer,
                NotificationSettings(sound = sound),
            ).contains("sound\"") shouldBe
            false
    }

    @Test
    fun `missing sound section gives defaults`() {
        decode("""{"popups": false}""").sound shouldBe SoundSettings()
        decode("""{"sound": {}}""").sound shouldBe SoundSettings()
    }

    @Test
    fun `sound volume is clamped`() {
        decode("""{"sound": {"volume": 500}}""").sound.volume shouldBe 100
        decode("""{"sound": {"volume": -5}}""").sound.volume shouldBe 0
    }

    @Test
    fun `a sound name that cannot be a file name is skipped`() {
        val sound = decode("""{"sound": {"sounds": {"failed": "../../etc/x", "needsInput": "Funk"}}}""").sound

        sound.sounds shouldBe mapOf(NotificationKind.NEEDS_INPUT to SoundId("Funk"))
    }

    @Test
    fun `a sound of the wrong type is refused`() {
        shouldThrow<SerializationException> { decode("""{"sound": {"sounds": {"failed": 3}}}""") }
        shouldThrow<SerializationException> { decode("""{"sound": {"volume": "loud"}}""") }
        shouldThrow<SerializationException> { decode("""{"sound": 1}""") }
    }
}
