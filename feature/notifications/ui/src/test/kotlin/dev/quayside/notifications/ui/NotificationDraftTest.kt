package dev.quayside.notifications.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.PopupPosition
import dev.quayside.notifications.api.PopupSettings
import dev.quayside.notifications.api.SpeechSettings
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/** `draftOf` and `settingsOf` (plan AM-704, table 1, part A): pure, the draft keeps numbers as text. */
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
                .withChannel(NotificationKind.TURN_ENDED, NotificationChannel.SOUND, true)
                .copy(popups = false, voice = false, runningLongMinutes = 45)

        val draft = draftOf(settings)

        draft.popups shouldBe false
        draft.voice shouldBe false
        draft.runningLongMinutes shouldBe "45"
        draft.matrix[NotificationKind.TURN_ENDED] shouldBe
            setOf(NotificationChannel.POPUP, NotificationChannel.SOUND)
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
    fun `fields the dialog does not show are kept from the current settings`() {
        val current =
            defaults.copy(
                popup = PopupSettings(position = PopupPosition.TOP_LEFT),
                speech = SpeechSettings(rate = 250, readQuestion = false),
            )

        val result = (settingsOf(draftOf(current).copy(popups = false), current) as Outcome.Ok).value

        result shouldBe current.copy(popups = false)
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
}
