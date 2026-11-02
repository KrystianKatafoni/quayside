package dev.quayside.notifications.api

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

/** [SpeechSettings] and [VoiceId]: the defaults of docs/07 and the limits (FR-N4, FR-N5). */
class SpeechSettingsTest {
    @Test
    fun `defaults follow docs 07`() {
        val s = SpeechSettings()

        s.voice shouldBe VoiceId("Zosia")
        s.rate shouldBe 190
        s.volume shouldBe 70
        s.templates shouldBe emptyMap()
        s.readQuestion shouldBe true
        s.onlyInBackground shouldBe true
        s.muteWhenMicrophoneActive shouldBe true
        NotificationSettings().speech shouldBe s
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("defaultTemplates")
    fun `default templates of the kinds`(
        kind: NotificationKind,
        expected: String,
    ) {
        SpeechSettings.defaultTemplate(kind) shouldBe expected
        SpeechSettings().templateFor(kind) shouldBe expected
    }

    @Test
    fun `every kind has a default template`() {
        NotificationKind.entries.forEach { SpeechSettings.defaultTemplate(it).isNotBlank() shouldBe true }
    }

    @Test
    fun `templateFor falls back to the default`() {
        val s = SpeechSettings(templates = mapOf(NotificationKind.FAILED to "Ups, {sesja}"))

        s.templateFor(NotificationKind.FAILED) shouldBe "Ups, {sesja}"
        s.templateFor(NotificationKind.PERMISSION) shouldBe SpeechSettings.defaultTemplate(NotificationKind.PERMISSION)
    }

    @ParameterizedTest(name = "rate {0}")
    @ValueSource(ints = [89, 361, 0, -1])
    fun `rejects a rate out of range`(rate: Int) {
        shouldThrow<IllegalArgumentException> { SpeechSettings(rate = rate) }
    }

    @ParameterizedTest(name = "volume {0}")
    @ValueSource(ints = [-1, 101])
    fun `rejects a volume out of range`(volume: Int) {
        shouldThrow<IllegalArgumentException> { SpeechSettings(volume = volume) }
    }

    @ParameterizedTest(name = "rate {0}, volume {1}")
    @MethodSource("limits")
    fun `accepts the limits`(
        rate: Int,
        volume: Int,
    ) {
        SpeechSettings(rate = rate, volume = volume).let {
            it.rate shouldBe rate
            it.volume shouldBe volume
        }
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = ["", " ", "  \t"])
    fun `VoiceId rejects blank`(name: String) {
        shouldThrow<IllegalArgumentException> { VoiceId(name) }
    }

    @Test
    fun `init accepts MIN_VOLUME and MAX_VOLUME`() {
        SpeechSettings(volume = SpeechSettings.MIN_VOLUME).volume shouldBe 0
        SpeechSettings(volume = SpeechSettings.MAX_VOLUME).volume shouldBe 100
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("templateProblems")
    fun `template problems`(
        template: String,
        expected: TemplateProblem?,
    ) {
        SpeechSettings.templateProblem(template) shouldBe expected
    }

    @Test
    fun `every default template has no problem`() {
        NotificationKind.entries.forEach {
            SpeechSettings.templateProblem(SpeechSettings.defaultTemplate(it)) shouldBe null
        }
    }

    companion object {
        private val OVER_LIMIT = SpeechSettings.MAX_TEMPLATE_LENGTH + 1

        @JvmStatic
        fun templateProblems(): List<Arguments> =
            listOf(
                arguments("{sesja} ]] x", TemplateProblem.EMBEDDED_COMMAND),
                arguments("[[", TemplateProblem.EMBEDDED_COMMAND),
                arguments("[ x ]", null),
                arguments("a".repeat(SpeechSettings.MAX_TEMPLATE_LENGTH), null),
                arguments("a".repeat(OVER_LIMIT), TemplateProblem.TOO_LONG),
                arguments("[[" + "a".repeat(OVER_LIMIT - 2), TemplateProblem.EMBEDDED_COMMAND),
                arguments("{nieznana}", null),
            )

        @JvmStatic
        fun defaultTemplates(): List<Arguments> =
            listOf(
                arguments(NotificationKind.NEEDS_INPUT, "Agent {sesja} w projekcie {projekt} czeka na odpowiedź"),
                arguments(NotificationKind.PERMISSION, "{sesja} prosi o zgodę na {narzędzie}"),
                arguments(NotificationKind.FAILED, "Uwaga, {sesja} zatrzymał się z błędem"),
                arguments(NotificationKind.TURN_ENDED, "{sesja} skończył turę"),
                arguments(NotificationKind.PULL_REQUEST, "{sesja} ma pull request do przeglądu"),
                arguments(NotificationKind.RUNNING_LONG, "{sesja} pracuje już długo"),
                arguments(NotificationKind.TERMINAL_BELL, "Dzwonek w terminalu"),
            )

        @JvmStatic
        fun limits(): List<Arguments> =
            listOf(
                arguments(SpeechSettings.MIN_RATE, 0),
                arguments(SpeechSettings.MAX_RATE, 100),
            )
    }
}
