package dev.quayside.notifications.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.PopupDuration
import dev.quayside.notifications.api.PopupPosition
import dev.quayside.notifications.api.PopupSettings
import dev.quayside.notifications.api.PopupStyle
import dev.quayside.notifications.api.SpeechSettings
import dev.quayside.notifications.api.VoiceId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/** `draftOf` and `settingsOf` (plan AM-704, table 1, parts A and B1): pure, the draft keeps numbers as text. */
class NotificationDraftTest {
    private val defaults = NotificationSettings()

    private fun NotificationDraft.with(
        kind: NotificationKind,
        channels: Set<NotificationChannel>,
    ): NotificationDraft = copy(matrix = matrix + (kind to channels))

    private fun saved(draft: NotificationDraft): NotificationSettings =
        (settingsOf(draft, defaults) as Outcome.Ok).value

    @Test
    fun `draft of the defaults has every kind with its default channels`() {
        val draft = draftOf(defaults)

        draft.matrix.keys shouldBe NotificationKind.entries.toSet()
        NotificationKind.entries.forEach { draft.matrix[it] shouldBe NotificationSettings.defaultChannels(it) }
        draft.popups shouldBe true
        draft.voice shouldBe true
        draft.runningLongMinutes shouldBe "30"
    }

    @Test
    fun `draft shows the saved switches, channels and threshold`() {
        val settings =
            defaults
                .withChannel(NotificationKind.TURN_ENDED, NotificationChannel.VOICE, true)
                .copy(popups = false, voice = false, runningLongMinutes = 45)

        val draft = draftOf(settings)

        draft.popups shouldBe false
        draft.voice shouldBe false
        draft.runningLongMinutes shouldBe "45"
        draft.matrix[NotificationKind.TURN_ENDED] shouldBe
            setOf(NotificationChannel.POPUP, NotificationChannel.SOUND, NotificationChannel.VOICE)
    }

    @Test
    fun `the draft of the defaults becomes an empty matrix`() {
        val result = settingsOf(draftOf(defaults), defaults)

        result shouldBe Outcome.Ok(defaults)
        saved(draftOf(defaults)).matrix shouldBe emptyMap()
    }

    @Test
    fun `a kind that differs from its default is stored alone`() {
        val draft =
            draftOf(defaults).with(
                NotificationKind.PULL_REQUEST,
                setOf(NotificationChannel.POPUP),
            )

        saved(draft).matrix shouldBe
            mapOf(NotificationKind.PULL_REQUEST to setOf(NotificationChannel.POPUP))
    }

    @Test
    fun `a kind with no channel at all is stored as an empty set`() {
        val draft = draftOf(defaults).with(NotificationKind.NEEDS_INPUT, emptySet())

        saved(draft).matrix shouldBe mapOf(NotificationKind.NEEDS_INPUT to emptySet())
    }

    @Test
    fun `a matrix changed and restored to the default is empty`() {
        val changed = draftOf(defaults).with(NotificationKind.PULL_REQUEST, setOf(NotificationChannel.POPUP))
        val restored =
            changed.with(
                NotificationKind.PULL_REQUEST,
                NotificationSettings.defaultChannels(NotificationKind.PULL_REQUEST),
            )

        saved(restored).matrix shouldBe emptyMap()
    }

    @Test
    fun `a stored deviation that is made default again disappears`() {
        val current = defaults.withChannel(NotificationKind.TURN_ENDED, NotificationChannel.VOICE, true)
        val draft =
            draftOf(current).with(
                NotificationKind.TURN_ENDED,
                NotificationSettings.defaultChannels(NotificationKind.TURN_ENDED),
            )

        (settingsOf(draft, current) as Outcome.Ok).value.matrix shouldBe emptyMap()
    }

    @Test
    fun `switches are taken from the draft`() {
        val result = saved(draftOf(defaults).copy(popups = false, voice = false))

        result.popups shouldBe false
        result.voice shouldBe false
    }

    @Test
    fun `a saved non default draft round trips unchanged`() {
        val current =
            defaults
                .withChannel(NotificationKind.TURN_ENDED, NotificationChannel.VOICE, true)
                .copy(
                    runningLongMinutes = 45,
                    popup = PopupSettings(PopupPosition.TOP_LEFT, PopupDuration.ALWAYS_STAY, 5, PopupStyle.COMPACT),
                    speech =
                        SpeechSettings(
                            voice = VoiceId("Ewa"),
                            rate = 250,
                            volume = 40,
                            templates = mapOf(NotificationKind.FAILED to "Ups, {sesja}"),
                            readQuestion = false,
                            onlyInBackground = false,
                            muteWhenMicrophoneActive = false,
                        ),
                )

        settingsOf(draftOf(current), current) shouldBe Outcome.Ok(current)
    }

    @Test
    fun `voice null round trips as the system voice`() {
        val current = defaults.copy(speech = SpeechSettings(voice = null))

        draftOf(current).speech.voice shouldBe null
        settingsOf(draftOf(current), current) shouldBe Outcome.Ok(current)
    }

    @ParameterizedTest(name = "\"{0}\" is {1} minutes")
    @CsvSource("1,1", "1440,1440", "' 45 ',45", "30,30")
    fun `threshold inside the range is accepted`(
        text: String,
        minutes: Int,
    ) {
        saved(draftOf(defaults).copy(runningLongMinutes = text)).runningLongMinutes shouldBe minutes
    }

    @ParameterizedTest(name = "\"{0}\" is refused")
    @ValueSource(strings = ["0", "1441", "abc", "", "-5", "1.5"])
    fun `threshold outside the range or not a number is refused`(text: String) {
        val result = settingsOf(draftOf(defaults).copy(runningLongMinutes = text), defaults)

        result shouldBe Outcome.Err(InvalidField(NotificationField.RUNNING_LONG_MINUTES))
    }

    @Test
    fun `draft shows the saved popup settings`() {
        val settings =
            defaults.copy(
                popup =
                    PopupSettings(
                        position = PopupPosition.TOP_CENTER,
                        duration = PopupDuration.ALWAYS_STAY,
                        maxVisible = 7,
                        style = PopupStyle.COMPACT,
                    ),
            )

        draftOf(settings).popup shouldBe
            PopupDraft(PopupPosition.TOP_CENTER, PopupDuration.ALWAYS_STAY, "7", PopupStyle.COMPACT)
    }

    @Test
    fun `popup choices are taken from the draft`() {
        val popup = PopupDraft(PopupPosition.BOTTOM_LEFT, PopupDuration.ALWAYS_TIMED, "6", PopupStyle.COMPACT)

        saved(draftOf(defaults).copy(popup = popup)).popup shouldBe
            PopupSettings(
                position = PopupPosition.BOTTOM_LEFT,
                duration = PopupDuration.ALWAYS_TIMED,
                maxVisible = 6,
                style = PopupStyle.COMPACT,
            )
    }

    @ParameterizedTest(name = "\"{0}\" is {1} popups")
    @CsvSource("1,1", "10,10", "' 4 ',4")
    fun `max visible inside the range is accepted`(
        text: String,
        count: Int,
    ) {
        val draft = draftOf(defaults)

        saved(draft.copy(popup = draft.popup.copy(maxVisible = text))).popup.maxVisible shouldBe count
    }

    @ParameterizedTest(name = "\"{0}\" is refused")
    @ValueSource(strings = ["0", "11", "abc", "", "-1", "2.5"])
    fun `max visible outside the range or not a number is refused`(text: String) {
        val draft = draftOf(defaults)

        settingsOf(draft.copy(popup = draft.popup.copy(maxVisible = text)), defaults) shouldBe
            Outcome.Err(InvalidField(NotificationField.MAX_VISIBLE))
    }

    @Test
    fun `threshold is checked before max visible`() {
        val draft = draftOf(defaults)

        val result =
            settingsOf(
                draft.copy(runningLongMinutes = "0", popup = draft.popup.copy(maxVisible = "0")),
                defaults,
            )

        result shouldBe Outcome.Err(InvalidField(NotificationField.RUNNING_LONG_MINUTES))
    }
}
