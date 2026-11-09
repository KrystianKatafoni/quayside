package dev.quayside.notifications.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.domain.Outcome
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.FakeSoundLibrary
import dev.quayside.notifications.api.FakeVoiceCatalog
import dev.quayside.notifications.api.FakeVoicePreview
import dev.quayside.notifications.api.VoiceCatalog
import dev.quayside.notifications.api.VoiceId
import dev.quayside.notifications.api.VoiceSample
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test

private const val PREVIEW_WAIT_MS = 5_000L

/**
 * Scenarios of the voice list, the example, the sample and the hint about Polish voices in the "Głos" section
 * (FR-N4, NFR-6, plan AM-813) on fakes. Companion of [NotificationSettingsVoiceScreenTest]; uses its `INSTALLED`.
 */
@OptIn(ExperimentalTestApi::class)
class NotificationSettingsPreviewScreenTest {
    private lateinit var preview: FakeVoicePreview

    /** Shows the dialog over default settings with [voices] and a sample port [sampler], and opens it. */
    private fun ComposeUiTest.showDialog(
        voices: VoiceCatalog = FakeVoiceCatalog(INSTALLED),
        sampler: FakeVoicePreview = FakeVoicePreview(),
    ) {
        preview = sampler
        val repo = FakeNotificationSettingsRepo()
        val built = mutableListOf<NotificationSettingsController>()
        setContent {
            AmTestTheme {
                val scope = rememberCoroutineScope()
                val controller =
                    remember {
                        NotificationSettingsController(
                            repo,
                            voices,
                            sampler,
                            MutableStateFlow(emptyList()),
                            scope,
                        ).also {
                            built +=
                                it
                        }
                    }
                NotificationSettingsDialog(controller, SoundPreviewController(FakeSoundLibrary(), scope))
            }
        }
        waitUntil(timeoutMillis = PREVIEW_WAIT_MS) { built.isNotEmpty() }
        runOnIdle { built.single().open() }
        await(SettingsTags.DIALOG)
    }

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag), PREVIEW_WAIT_MS)
        return onNodeWithTag(tag)
    }

    private fun ComposeUiTest.awaitText(
        tag: String,
        text: String,
    ) {
        waitUntilExactlyOneExists(hasTestTag(tag) and hasText(text, substring = true), PREVIEW_WAIT_MS)
    }

    @Test
    fun `list shows the language names`() =
        runComposeUiTest {
            // Given the open dialog with the list of voices loaded
            showDialog()
            awaitText(SettingsTags.SPEECH_VOICE, "Zosia — polski (Polska)")

            // When the user opens the list
            openList(SettingsTags.SPEECH_VOICE)

            // Then every row names the language in Polish and none shows the raw code
            optionShows(1, "Ewa — polski (Polska)")
            optionShows(3, "Alex — angielski (Stany Zjednoczone)")
            optionShows(5, "Zuzana — czeski (Czechy)")
            listOf("pl_PL", "en_US", "cs_CZ", "en_GB").forEach { code ->
                onNode(hasText(code, substring = true)).assertDoesNotExist()
            }
        }

    @Test
    fun `sample of the selected voice is shown`() =
        runComposeUiTest {
            // Given the open dialog with the saved voice Zosia
            showDialog()

            // Then the caption under the field shows her example
            awaitText(SettingsTags.SPEECH_SAMPLE, "Przykład: Witaj, nazywam się Zosia.")

            // When the user picks Daniel, the caption follows
            pickOption(SettingsTags.SPEECH_VOICE, 4)
            awaitText(SettingsTags.SPEECH_SAMPLE, "Przykład: Hello, my name is Daniel.")

            // And the system voice has no example
            pickOption(SettingsTags.SPEECH_VOICE, 0)
            waitUntil(timeoutMillis = PREVIEW_WAIT_MS) {
                onAllNodesWithTag(SettingsTags.SPEECH_SAMPLE).fetchSemanticsNodes().isEmpty()
            }
        }

    @Test
    fun `preview button plays and then offers stop`() =
        runComposeUiTest {
            // Given the open dialog with Zosia, rate 190, volume 70, and a sample that plays until stopped
            val sampler = FakeVoicePreview().apply { gate = CompletableDeferred() }
            showDialog(sampler = sampler)
            awaitText(SettingsTags.PREVIEW, "Odsłuchaj")
            onNodeWithTag(SettingsTags.PREVIEW).assertContentDescriptionContains("Zosia", substring = true)

            // When the user clicks the button
            await(SettingsTags.PREVIEW).performScrollTo().performClick()

            // Then Zosia says her example with the drafted rate and volume, and the button offers to stop
            awaitText(SettingsTags.PREVIEW, "Zatrzymaj")
            onNodeWithTag(SettingsTags.PREVIEW).assertContentDescriptionContains("Zosia", substring = true)
            sampler.played shouldBe
                listOf(VoiceSample(VoiceId("Zosia"), "Witaj, nazywam się Zosia.", rate = 190, volume = 70))

            // And a click on it stops the sample
            onNodeWithTag(SettingsTags.PREVIEW).performClick()
            awaitText(SettingsTags.PREVIEW, "Odsłuchaj")
            sampler.cancelled shouldBe 1
        }

    @Test
    fun `the end of the sample offers to play again`() =
        runComposeUiTest {
            // Given a sample that is playing
            val sampler = FakeVoicePreview().apply { gate = CompletableDeferred() }
            showDialog(sampler = sampler)
            await(SettingsTags.PREVIEW).performScrollTo().performClick()
            awaitText(SettingsTags.PREVIEW, "Zatrzymaj")

            // When it ends
            runOnIdle { sampler.gate?.complete(Unit) }

            // Then the button offers it again and no failure shows
            awaitText(SettingsTags.PREVIEW, "Odsłuchaj")
            onNodeWithTag(SettingsTags.PREVIEW_FAILED).assertDoesNotExist()
        }

    @Test
    fun `preview failure shows a caption`() =
        runComposeUiTest {
            // Given a sample that fails
            showDialog(sampler = FakeVoicePreview.failing())

            // When the user clicks the button
            await(SettingsTags.PREVIEW).performScrollTo().performClick()

            // Then the caption shows and the button offers it again
            await(SettingsTags.PREVIEW_FAILED)
            awaitText(SettingsTags.PREVIEW, "Odsłuchaj")

            // And a change in the dialog takes the caption away
            pickOption(SettingsTags.SPEECH_VOICE, 4)
            waitUntil(timeoutMillis = PREVIEW_WAIT_MS) {
                onAllNodesWithTag(SettingsTags.PREVIEW_FAILED).fetchSemanticsNodes().isEmpty()
            }
        }

    @Test
    fun `one polish voice shows the hint with its name`() =
        runComposeUiTest {
            // Given a Mac with one Polish voice
            showDialog(voices = FakeVoiceCatalog(listOf(INSTALLED[1], INSTALLED[4])))

            // Then the hint names it and the way to more voices
            awaitText(SettingsTags.POLISH_HINT, "Jedyny polski głos to Zosia")
            awaitText(
                SettingsTags.POLISH_HINT,
                "Ustawienia systemowe → Dostępność → Treść mówiona → Głos systemowy → Zarządzaj głosami",
            )
        }

    @Test
    fun `no polish voice shows the hint`() =
        runComposeUiTest {
            // Given a Mac without a Polish voice
            showDialog(voices = FakeVoiceCatalog(listOf(INSTALLED[4])))

            // Then the hint says so and shows the way
            awaitText(SettingsTags.POLISH_HINT, "Brak polskiego głosu")
            awaitText(SettingsTags.POLISH_HINT, "Dostępność → Treść mówiona")
        }

    @Test
    fun `two polish voices show no hint`() =
        runComposeUiTest {
            // Given a Mac with two Polish voices and the list loaded
            showDialog()
            awaitText(SettingsTags.SPEECH_VOICE, "Zosia — polski (Polska)")

            // Then there is no hint
            onNodeWithTag(SettingsTags.POLISH_HINT).assertDoesNotExist()
        }

    @Test
    fun `no hint while voices load`() =
        runComposeUiTest {
            // Given a list of voices that is still loading
            val voices = ScriptedVoiceCatalog(Outcome.Ok(listOf(INSTALLED[4])), hold = true)
            showDialog(voices = voices)

            // Then there is no hint
            onNodeWithTag(SettingsTags.POLISH_HINT).assertDoesNotExist()

            // When the list arrives, the hint shows
            runOnIdle { voices.release.complete(Unit) }
            awaitText(SettingsTags.POLISH_HINT, "Brak polskiego głosu")
        }

    @Test
    fun `no hint after a failed list`() =
        runComposeUiTest {
            // Given a Mac whose list of voices cannot be read
            showDialog(voices = FakeVoiceCatalog.failing())
            await(SettingsTags.VOICES_FAILED)

            // Then there is no hint
            onNodeWithTag(SettingsTags.POLISH_HINT).assertDoesNotExist()
        }
}
