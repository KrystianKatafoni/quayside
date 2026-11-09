package dev.quayside.notifications.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.SoundId
import dev.quayside.notifications.api.SoundSettings
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** The sound part of `draftOf` and `settingsOf` (FR-N1): the volume stays text, only deviations are saved. */
class NotificationSoundDraftTest {
    private val defaults = NotificationSettings()

    private fun result(draft: NotificationDraft) = settingsOf(draft, defaults)

    @Test
    fun `draft of the defaults has every kind with its default sound`() {
        val draft = draftOf(defaults).sound

        draft.volume shouldBe "100"
        draft.sounds.keys shouldBe NotificationKind.entries.toSet()
        NotificationKind.entries.forEach { draft.sounds[it] shouldBe SoundSettings.defaultSound(it) }
    }

    @Test
    fun `draft shows the saved volume and sound`() {
        val settings =
            defaults.copy(sound = SoundSettings(25, mapOf(NotificationKind.FAILED to SoundId("Funk"))))

        val draft = draftOf(settings).sound

        draft.volume shouldBe "25"
        draft.sounds[NotificationKind.FAILED] shouldBe SoundId("Funk")
        draft.sounds[NotificationKind.TURN_ENDED] shouldBe SoundId("Hero")
    }

    @Test
    fun `the draft of the defaults saves no sound deviation`() {
        (result(draftOf(defaults)) as Outcome.Ok).value.sound shouldBe SoundSettings()
    }

    @Test
    fun `only a sound that differs from the default is saved`() {
        val draft = draftOf(defaults)
        val edited =
            draft.copy(
                sound =
                    draft.sound.copy(
                        volume = " 40 ",
                        sounds =
                            draft.sound.sounds +
                                (NotificationKind.FAILED to SoundId("Funk")) +
                                (NotificationKind.TURN_ENDED to SoundId("Hero")),
                    ),
            )

        (result(edited) as Outcome.Ok).value.sound shouldBe
            SoundSettings(40, mapOf(NotificationKind.FAILED to SoundId("Funk")))
    }

    @ParameterizedTest(name = "volume \"{0}\" is refused")
    @ValueSource(strings = ["", "abc", "-1", "101", "1.5"])
    fun `a bad volume is refused`(text: String) {
        val draft = draftOf(defaults)

        result(draft.copy(sound = draft.sound.copy(volume = text))) shouldBe
            Outcome.Err(InvalidField(NotificationField.SOUND_VOLUME))
    }

    @ParameterizedTest(name = "volume \"{0}\" is accepted")
    @ValueSource(strings = ["0", "100"])
    fun `the ends of the volume range are accepted`(text: String) {
        val draft = draftOf(defaults)

        (result(draft.copy(sound = draft.sound.copy(volume = text))) as Outcome.Ok).value.sound.volume shouldBe
            text.toInt()
    }

    @Test
    fun `the message of the sound volume names the range`() {
        val text = SettingsStrings.of(InvalidField(NotificationField.SOUND_VOLUME))

        text.contains("od 0 do 100") shouldBe true
    }

    @Test
    fun `the saved sound stays among the choices when it is not installed`() {
        soundChoices(listOf(SoundId("Glass")), SoundId("Funk")) shouldBe listOf(SoundId("Funk"), SoundId("Glass"))
        soundChoices(listOf(SoundId("Glass")), SoundId("Glass")) shouldBe listOf(SoundId("Glass"))
        soundChoices(null, SoundId("Funk")) shouldBe listOf(SoundId("Funk"))
    }
}
