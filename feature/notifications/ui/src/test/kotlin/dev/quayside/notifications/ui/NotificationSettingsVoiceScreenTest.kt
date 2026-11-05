package dev.quayside.notifications.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.test.withKeyDown
import dev.quayside.core.designsystem.AmDropdownTags
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.FakeVoiceCatalog
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.SpeechSettings
import dev.quayside.notifications.api.VoiceId
import dev.quayside.notifications.api.VoiceOption
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Cold JVM start-up can exceed the default 1 s of `waitUntil` in the first test. */
private const val WAIT_MS = 5_000L

/** Voices of the fake Mac. With the saved default Zosia the choice is: system, Ewa, Zosia, Alex, Daniel, Zuzana. */
private val INSTALLED =
    listOf(
        VoiceOption(VoiceId("Zuzana"), "cs_CZ"),
        VoiceOption(VoiceId("Zosia"), "pl_PL"),
        VoiceOption(VoiceId("Ewa"), "pl_PL"),
        VoiceOption(VoiceId("Daniel"), "en_GB"),
        VoiceOption(VoiceId("Alex"), "en_US"),
    )

/** Tab order of the dialog from the popup style to the Save button (plan AM-704, part B2b). */
private val SPEECH_ORDER =
    listOf(SettingsTags.POPUP_STYLE, SettingsTags.SPEECH_VOICE, SettingsTags.RATE, SettingsTags.VOLUME) +
        NotificationKind.entries.flatMap { listOf(SettingsTags.template(it), SettingsTags.restore(it)) } +
        listOf(
            SettingsTags.READ_QUESTION,
            SettingsTags.ONLY_IN_BACKGROUND,
            SettingsTags.MUTE_MICROPHONE,
            SettingsTags.SAVE,
        )

/**
 * Scenarios of the "Głos" section of the notification settings dialog (FR-N4, NFR-6) on a fake repo and a fake list
 * of voices. Companion of [NotificationSettingsDialogScreenTest] (plan AM-704, part B2b).
 */
@OptIn(ExperimentalTestApi::class)
class NotificationSettingsVoiceScreenTest {
    private var repo = FakeNotificationSettingsRepo()

    /** Shows the dialog over a repo with [saved] settings and a catalog of [INSTALLED] voices, and opens it. */
    private fun ComposeUiTest.showDialog(
        saved: NotificationSettings = NotificationSettings(),
        voices: FakeVoiceCatalog = FakeVoiceCatalog(INSTALLED),
    ) {
        repo = FakeNotificationSettingsRepo(saved)
        val built = mutableListOf<NotificationSettingsController>()
        setContent {
            AmTestTheme {
                val scope = rememberCoroutineScope()
                val controller = remember { NotificationSettingsController(repo, voices, scope).also { built += it } }
                NotificationSettingsDialog(controller)
            }
        }
        waitUntil(timeoutMillis = WAIT_MS) { built.isNotEmpty() }
        runOnIdle { built.single().open() }
        await(SettingsTags.DIALOG)
    }

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag), WAIT_MS)
        return onNodeWithTag(tag)
    }

    private fun ComposeUiTest.awaitText(
        tag: String,
        text: String,
    ) {
        waitUntilExactlyOneExists(hasTestTag(tag) and hasText(text, substring = true), WAIT_MS)
    }

    private fun ComposeUiTest.awaitFocus(tag: String) {
        waitUntilExactlyOneExists(hasTestTag(tag) and isFocused(), WAIT_MS)
    }

    private fun ComposeUiTest.press(
        tag: String,
        key: Key,
    ) {
        onNodeWithTag(tag).performKeyInput { pressKey(key) }
    }

    private fun ComposeUiTest.save() {
        await(SettingsTags.SAVE).performScrollTo().performClick()
    }

    private fun ComposeUiTest.awaitClosed() {
        waitUntil(timeoutMillis = WAIT_MS) {
            onAllNodesWithTag(SettingsTags.DIALOG).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `tab moves through the voice section in order`() =
        runComposeUiTest {
            // Given the open dialog and the focus on the popup style
            showDialog()
            await(SettingsTags.POPUP_STYLE).performScrollTo().focus()
            awaitFocus(SettingsTags.POPUP_STYLE)

            // When the user presses Tab, once per control
            SPEECH_ORDER.zipWithNext().forEach { (from, to) ->
                press(from, Key.Tab)

                // Then the focus moves to the next one: voice, rate, volume, 7 x (template, restore), 3 switches, Save
                awaitFocus(to)
            }
        }

    @Test
    fun `voice is picked from the list and saved`() =
        runComposeUiTest {
            // Given the open dialog with the saved voice Zosia and the list of voices loaded
            showDialog()
            awaitText(SettingsTags.SPEECH_VOICE, "Zosia (pl_PL)")
            onNodeWithTag(SettingsTags.VOICES_FAILED).assertDoesNotExist()

            // When the user opens the list: the system voice, the saved one and the installed ones are there
            openList(SettingsTags.SPEECH_VOICE)
            val labels =
                listOf(
                    "Głos systemowy",
                    "Ewa (pl_PL)",
                    "Zosia (pl_PL)",
                    "Alex (en_US)",
                    "Daniel (en_GB)",
                    "Zuzana (cs_CZ)",
                )
            labels.forEachIndexed { index, label -> optionShows(index, label) }
            onNodeWithTag(AmDropdownTags.option(labels.size)).assertDoesNotExist()
            onNodeWithTag(AmDropdownTags.option(2)).assertIsSelected()

            // And picks Daniel and saves
            clickOption(4)
            awaitText(SettingsTags.SPEECH_VOICE, "Daniel (en_GB)")
            save()

            // Then the repo holds Daniel
            awaitClosed()
            repo.settings.value.speech.voice shouldBe VoiceId("Daniel")
        }

    @Test
    fun `the system voice is picked from the list and keeps no voice name`() =
        runComposeUiTest {
            // Given the open dialog with the saved voice Zosia
            showDialog()
            awaitText(SettingsTags.SPEECH_VOICE, "Zosia (pl_PL)")

            // When the user picks the system voice, the first of the list, and saves
            pickOption(SettingsTags.SPEECH_VOICE, 0)
            awaitText(SettingsTags.SPEECH_VOICE, "Głos systemowy")
            save()

            // Then the repo holds no voice
            awaitClosed()
            repo.settings.value.speech.voice shouldBe null
        }

    @Test
    fun `the side arrows on the closed voice field open the list and do not change the voice`() =
        runComposeUiTest {
            // Given the open dialog and the focus on the voice field
            showDialog()
            awaitText(SettingsTags.SPEECH_VOICE, "Zosia (pl_PL)")
            await(SettingsTags.SPEECH_VOICE).performScrollTo().focus()
            awaitFocus(SettingsTags.SPEECH_VOICE)

            // When the user presses Right and closes the list with Escape
            press(SettingsTags.SPEECH_VOICE, Key.DirectionRight)
            awaitListOpen()
            pressKeyOn(SettingsTags.SPEECH_VOICE, Key.Escape)
            awaitListGone()

            // Then the voice stays
            awaitText(SettingsTags.SPEECH_VOICE, "Zosia (pl_PL)")
        }

    @Test
    fun `a letter jumps to the next voice with that letter`() =
        runComposeUiTest {
            // Given the open dialog and the focus on the closed voice field, which shows Zosia
            showDialog()
            awaitText(SettingsTags.SPEECH_VOICE, "Zosia (pl_PL)")
            await(SettingsTags.SPEECH_VOICE).performScrollTo().focus()
            awaitFocus(SettingsTags.SPEECH_VOICE)

            // When the user presses D, Z, Z, Q and E, then the choice follows
            val steps =
                listOf(
                    Key.D to "Daniel (en_GB)",
                    Key.Z to "Zuzana (cs_CZ)",
                    // wraps round the end of the list to the Polish Zosia
                    Key.Z to "Zosia (pl_PL)",
                    // no voice starts with Q: the choice stays
                    Key.Q to "Zosia (pl_PL)",
                    Key.E to "Ewa (pl_PL)",
                )
            steps.forEach { (key, label) ->
                press(SettingsTags.SPEECH_VOICE, key)
                awaitText(SettingsTags.SPEECH_VOICE, label)
            }
        }

    @Test
    fun `a letter with a modifier key does not change the choice`() =
        runComposeUiTest {
            // Given the open dialog and the focus on the closed voice field, which shows Zosia
            showDialog()
            awaitText(SettingsTags.SPEECH_VOICE, "Zosia (pl_PL)")
            await(SettingsTags.SPEECH_VOICE).performScrollTo().focus()
            awaitFocus(SettingsTags.SPEECH_VOICE)

            // When the user presses D with Cmd, Ctrl or Alt held
            listOf(Key.MetaLeft, Key.CtrlLeft, Key.AltLeft).forEach { modifier ->
                onNodeWithTag(SettingsTags.SPEECH_VOICE).performKeyInput {
                    withKeyDown(modifier) { pressKey(Key.D) }
                }
            }

            // Then the choice stays on Zosia; a plain D still jumps (the field was listening)
            awaitText(SettingsTags.SPEECH_VOICE, "Zosia (pl_PL)")
            press(SettingsTags.SPEECH_VOICE, Key.D)
            awaitText(SettingsTags.SPEECH_VOICE, "Daniel (en_GB)")
        }

    @Test
    fun `saved voice that is not installed is labelled`() =
        runComposeUiTest {
            // Given a saved voice that is not on the list
            showDialog(saved = NotificationSettings(speech = SpeechSettings(voice = VoiceId("Ghost"))))

            // Then once the list is loaded the voice is labelled as not installed, in the field and in the list
            awaitText(SettingsTags.SPEECH_VOICE, "Ghost (niezainstalowany)")
            openList(SettingsTags.SPEECH_VOICE)
            optionShows(1, "Ghost (niezainstalowany)")
            onNodeWithTag(AmDropdownTags.option(1)).assertIsSelected()

            // And after another voice is picked, the saved one is still in the list with its label
            clickOption(2)
            awaitText(SettingsTags.SPEECH_VOICE, "Ewa (pl_PL)")
            openList(SettingsTags.SPEECH_VOICE)
            optionShows(1, "Ghost (niezainstalowany)")
            clickOption(1)
            awaitText(SettingsTags.SPEECH_VOICE, "Ghost (niezainstalowany)")

            // And saving keeps the voice
            save()
            awaitClosed()
            repo.settings.value.speech.voice shouldBe VoiceId("Ghost")
        }

    @Test
    fun `a failed list of voices shows its note and the choice still works`() =
        runComposeUiTest {
            // Given a Mac whose list of voices cannot be read
            showDialog(voices = FakeVoiceCatalog.failing())

            // Then the note shows and the saved voice is shown by its name alone
            awaitText(SettingsTags.VOICES_FAILED, "Nie udało się wczytać listy głosów.")
            awaitText(SettingsTags.SPEECH_VOICE, "Zosia")

            // When the user opens the list it holds the system voice and the saved one, and picks the system voice
            openList(SettingsTags.SPEECH_VOICE)
            optionShows(0, "Głos systemowy")
            optionShows(1, "Zosia")
            onNodeWithTag(AmDropdownTags.option(2)).assertDoesNotExist()
            clickOption(0)
            awaitText(SettingsTags.SPEECH_VOICE, "Głos systemowy")
        }

    @Test
    fun `restore default puts the default text`() =
        runComposeUiTest {
            // Given a saved custom message for FAILED
            val custom = SpeechSettings(templates = mapOf(NotificationKind.FAILED to "Ups, {sesja}"))
            showDialog(saved = NotificationSettings(speech = custom))
            awaitText(SettingsTags.template(NotificationKind.FAILED), "Ups, {sesja}")

            // When the user clicks "Przywróć domyślny" of that row and saves
            await(SettingsTags.restore(NotificationKind.FAILED)).performScrollTo().performClick()

            // Then the field shows the default text and the saved settings keep no custom message
            awaitText(SettingsTags.template(NotificationKind.FAILED), "Uwaga, {sesja} zatrzymał się z błędem")
            save()
            awaitClosed()
            repo.settings.value.speech.templates shouldBe emptyMap()
        }

    @Test
    fun `template with double brackets shows its message and the dialog stays`() =
        runComposeUiTest {
            // Given the open dialog
            showDialog()

            // When the user types an embedded command into a message and saves
            await(SettingsTags.template(NotificationKind.PERMISSION))
                .performScrollTo()
                .performTextReplacement("[[rate 500]] x")
            save()

            // Then exactly one message shows, the dialog is open and nothing is saved
            val field = InvalidField(NotificationField.TEMPLATE, NotificationKind.PERMISSION)
            awaitText(SettingsTags.invalid(NotificationField.TEMPLATE), "Komunikat: najwyżej 200 znaków, bez [[ i ]].")
            SettingsStrings.of(field) shouldBe "Komunikat: najwyżej 200 znaków, bez [[ i ]]."
            onNodeWithTag(SettingsTags.DIALOG).assertExists()
            repo.settings.value.speech.templates shouldBe emptyMap()
        }

    @Test
    fun `rate out of range shows its message and the dialog stays`() =
        runComposeUiTest {
            // Given the open dialog
            showDialog()

            // When the user types a rate that is too high and saves
            await(SettingsTags.RATE).performScrollTo().performTextReplacement("999")
            save()

            // Then the message sits with the field, the dialog is open and the rate is unchanged
            awaitText(SettingsTags.invalid(NotificationField.RATE), "Tempo: liczba słów na minutę od 90 do 360.")
            onNodeWithTag(SettingsTags.DIALOG).assertExists()
            repo.settings.value.speech.rate shouldBe SpeechSettings.DEFAULT_RATE
        }

    @Test
    fun `rate and volume typed are saved`() =
        runComposeUiTest {
            // Given the open dialog
            showDialog()

            // When the user types a rate and a volume and saves
            await(SettingsTags.RATE).performScrollTo().performTextReplacement("250")
            await(SettingsTags.VOLUME).performScrollTo().performTextReplacement("40")
            save()

            // Then the repo holds both
            awaitClosed()
            repo.settings.value.speech.rate shouldBe 250
            repo.settings.value.speech.volume shouldBe 40
        }

    @Test
    fun `speech switches are saved`() =
        runComposeUiTest {
            // Given the open dialog with the three switches on (the default)
            showDialog()

            // When the user switches "Czytaj treść pytania" and "Wycisz przy aktywnym mikrofonie" off and saves
            await(SettingsTags.READ_QUESTION).performScrollTo().performClick()
            await(SettingsTags.MUTE_MICROPHONE).performScrollTo().performClick()
            save()

            // Then those two are off and "Tylko gdy aplikacja jest w tle" stays on
            awaitClosed()
            repo.settings.value.speech.readQuestion shouldBe false
            repo.settings.value.speech.muteWhenMicrophoneActive shouldBe false
            repo.settings.value.speech.onlyInBackground shouldBe true
        }

    @Test
    fun `microphone switch shows the not yet working note`() =
        runComposeUiTest {
            // Given the open dialog
            showDialog()

            // Then the microphone switch is followed by the note that it does not work yet (ADR 0018)
            await(SettingsTags.MUTE_MICROPHONE).performScrollTo()
            awaitText(SettingsTags.MIC_NOTE, "Jeszcze nie działa: wykrywanie mikrofonu czeka na ADR 0018")
        }

    @Test
    fun `restore buttons name their kind for the screen reader`() =
        runComposeUiTest {
            // Given the open dialog
            showDialog()

            // Then each of the seven buttons has its own description
            val descriptions = NotificationKind.entries.map { SettingsStrings.restoreLabel(it) }
            descriptions.toSet().size shouldBe NotificationKind.entries.size
            NotificationKind.entries.zip(descriptions).forEach { (kind, text) ->
                await(SettingsTags.restore(kind)).assertContentDescriptionEquals(text)
            }
            descriptions.first() shouldBe "Przywróć domyślny: Agent czeka na odpowiedź"
        }

    @Test
    fun `fields have their name for the screen reader`() =
        runComposeUiTest {
            // Given the open dialog
            showDialog()

            // Then every field is described by the name of its row (S3)
            val names =
                listOf(
                    SettingsTags.RUNNING_LONG_MINUTES to "Długa praca po (minuty)",
                    SettingsTags.POPUP_POSITION to "Pozycja",
                    SettingsTags.POPUP_DURATION to "Czas wyświetlania",
                    SettingsTags.MAX_VISIBLE to "Maks. naraz",
                    SettingsTags.POPUP_STYLE to "Styl",
                    SettingsTags.SPEECH_VOICE to "Głos lektora",
                    SettingsTags.RATE to "Tempo (słowa na minutę)",
                    SettingsTags.VOLUME to "Głośność (%)",
                ) + NotificationKind.entries.map { SettingsTags.template(it) to SettingsStrings.of(it) }
            names.forEach { (tag, name) -> await(tag).assertContentDescriptionEquals(name) }
        }
}
