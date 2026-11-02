package dev.quayside.notifications.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.SpeechSettings
import dev.quayside.notifications.api.VoiceId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/** `draftOf` and `settingsOf` for the speech part (plan AM-704, table 1, part B2a): pure, numbers stay text. */
class NotificationSpeechDraftTest {
    private val defaults = NotificationSettings()

    private fun saved(draft: NotificationDraft): NotificationSettings =
        (settingsOf(draft, defaults) as Outcome.Ok).value

    @Test
    fun `draft shows the saved speech settings`() {
        val speech =
            SpeechSettings(
                voice = VoiceId("Ewa"),
                rate = 250,
                volume = 40,
                templates = mapOf(NotificationKind.FAILED to "Ups, {sesja}"),
                readQuestion = false,
                onlyInBackground = false,
                muteWhenMicrophoneActive = false,
            )

        val draft = draftOf(defaults.copy(speech = speech)).speech

        draft.voice shouldBe VoiceId("Ewa")
        draft.rate shouldBe "250"
        draft.volume shouldBe "40"
        draft.templates.keys shouldBe NotificationKind.entries.toSet()
        draft.templates[NotificationKind.FAILED] shouldBe "Ups, {sesja}"
        draft.templates[NotificationKind.TURN_ENDED] shouldBe
            SpeechSettings.defaultTemplate(NotificationKind.TURN_ENDED)
        draft.readQuestion shouldBe false
        draft.onlyInBackground shouldBe false
        draft.muteWhenMicrophoneActive shouldBe false
    }

    @Test
    fun `speech choices are taken from the draft`() {
        val draft = draftOf(defaults)
        val speech =
            draft.speech.copy(
                voice = null,
                rate = "120",
                volume = "10",
                readQuestion = false,
                onlyInBackground = false,
                muteWhenMicrophoneActive = false,
            )

        saved(draft.copy(speech = speech)).speech shouldBe
            SpeechSettings(
                voice = null,
                rate = 120,
                volume = 10,
                readQuestion = false,
                onlyInBackground = false,
                muteWhenMicrophoneActive = false,
            )
    }

    @ParameterizedTest(name = "\"{0}\" is {1} words per minute")
    @CsvSource("90,90", "360,360", "' 200 ',200")
    fun `rate inside the range is accepted`(
        text: String,
        rate: Int,
    ) {
        val draft = draftOf(defaults)

        saved(draft.copy(speech = draft.speech.copy(rate = text))).speech.rate shouldBe rate
    }

    @ParameterizedTest(name = "\"{0}\" is refused")
    @ValueSource(strings = ["89", "361", "abc", "", "-1", "190.5"])
    fun `rate outside the range or not a number is refused`(text: String) {
        val draft = draftOf(defaults)

        settingsOf(draft.copy(speech = draft.speech.copy(rate = text)), defaults) shouldBe
            Outcome.Err(InvalidField(NotificationField.RATE))
    }

    @ParameterizedTest(name = "\"{0}\" is {1} percent")
    @CsvSource("0,0", "100,100", "' 55 ',55")
    fun `volume inside the range is accepted`(
        text: String,
        volume: Int,
    ) {
        val draft = draftOf(defaults)

        saved(draft.copy(speech = draft.speech.copy(volume = text))).speech.volume shouldBe volume
    }

    @ParameterizedTest(name = "\"{0}\" is refused")
    @ValueSource(strings = ["-1", "101", "abc", "", "50.5"])
    fun `volume outside the range or not a number is refused`(text: String) {
        val draft = draftOf(defaults)

        settingsOf(draft.copy(speech = draft.speech.copy(volume = text)), defaults) shouldBe
            Outcome.Err(InvalidField(NotificationField.VOLUME))
    }

    @Test
    fun `template equal to the default or blank is not stored`() {
        val draft = draftOf(defaults)
        val templates =
            draft.templates(
                NotificationKind.FAILED to SpeechSettings.defaultTemplate(NotificationKind.FAILED),
                NotificationKind.TURN_ENDED to "",
                NotificationKind.PERMISSION to "   ",
            )

        saved(templates).speech.templates shouldBe emptyMap()
    }

    @Test
    fun `a template is trimmed and stored for its kind alone`() {
        val draft = draftOf(defaults).templates(NotificationKind.FAILED to "  x  ")

        saved(draft).speech.templates shouldBe mapOf(NotificationKind.FAILED to "x")
    }

    @Test
    fun `a stored template restored to the default is removed`() {
        val current = defaults.copy(speech = SpeechSettings(templates = mapOf(NotificationKind.FAILED to "Ups")))
        val draft =
            draftOf(current).templates(
                NotificationKind.FAILED to SpeechSettings.defaultTemplate(NotificationKind.FAILED),
            )

        (settingsOf(draft, current) as Outcome.Ok).value.speech.templates shouldBe emptyMap()
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = ["[[rate 500]] x", "x ]] y", "[["])
    fun `template with a problem is refused for its kind`(text: String) {
        val draft = draftOf(defaults).templates(NotificationKind.PERMISSION to text)

        settingsOf(draft, defaults) shouldBe
            Outcome.Err(InvalidField(NotificationField.TEMPLATE, NotificationKind.PERMISSION))
    }

    @Test
    fun `a template over the length limit is refused for its kind`() {
        val text = "a".repeat(SpeechSettings.MAX_TEMPLATE_LENGTH + 1)
        val draft = draftOf(defaults).templates(NotificationKind.TERMINAL_BELL to text)

        settingsOf(draft, defaults) shouldBe
            Outcome.Err(InvalidField(NotificationField.TEMPLATE, NotificationKind.TERMINAL_BELL))
    }

    @Test
    fun `a template of the maximal length is accepted`() {
        val text = "a".repeat(SpeechSettings.MAX_TEMPLATE_LENGTH)
        val draft = draftOf(defaults).templates(NotificationKind.FAILED to text)

        saved(draft).speech.templates shouldBe mapOf(NotificationKind.FAILED to text)
    }

    @Test
    fun `templates are checked in the order of the kinds`() {
        val kinds = NotificationKind.entries
        val draft = draftOf(defaults).templates(kinds[3] to "[[", kinds[1] to "]]")

        settingsOf(draft, defaults) shouldBe Outcome.Err(InvalidField(NotificationField.TEMPLATE, kinds[1]))
    }

    @Test
    fun `speech fields are checked after max visible in dialog order`() {
        val draft = draftOf(defaults)

        fun refused(
            speech: SpeechDraft,
            visible: String = "3",
        ) = settingsOf(draft.copy(popup = draft.popup.copy(maxVisible = visible), speech = speech), defaults)

        val allBad =
            draft.speech.copy(
                rate = "0",
                volume = "x",
                templates =
                    draft.speech.templates + (NotificationKind.FAILED to "[["),
            )

        refused(allBad, visible = "0") shouldBe Outcome.Err(InvalidField(NotificationField.MAX_VISIBLE))
        refused(allBad) shouldBe Outcome.Err(InvalidField(NotificationField.RATE))
        refused(allBad.copy(rate = "200")) shouldBe Outcome.Err(InvalidField(NotificationField.VOLUME))
        refused(allBad.copy(rate = "200", volume = "50")) shouldBe
            Outcome.Err(InvalidField(NotificationField.TEMPLATE, NotificationKind.FAILED))
    }

    private fun NotificationDraft.templates(vararg changes: Pair<NotificationKind, String>): NotificationDraft =
        copy(speech = speech.copy(templates = speech.templates + changes))
}
