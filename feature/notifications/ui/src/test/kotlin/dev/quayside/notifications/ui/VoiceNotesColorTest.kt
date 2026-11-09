package dev.quayside.notifications.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.VoiceId
import dev.quayside.notifications.api.VoiceOption
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private const val WAIT_MS = 5_000L

/** The colours of the captions under the voice choice (NFR-6, AM-813): muted for the notes, err for failures. */
@OptIn(ExperimentalTestApi::class)
class VoiceNotesColorTest {
    private val muted =
        SampleTokens
            .colors()
            .content.muted
            .toColor()
            .toArgb()
    private val err =
        SampleTokens
            .colors()
            .status.err
            .toColor()
            .toArgb()

    /** The ARGB values of all pixels of the node [tag]; the glyph stems have the exact colour of the text. */
    private fun ComposeUiTest.pixels(tag: String): Set<Int> {
        waitUntilExactlyOneExists(hasTestTag(tag), WAIT_MS)
        val map = onNodeWithTag(tag).captureToImage().toPixelMap()
        return (0 until map.width)
            .flatMap { x -> (0 until map.height).map { y -> map[x, y].toArgb() } }
            .toSet()
    }

    @Test
    fun `notes are muted and the preview failure is an error colour`() =
        runComposeUiTest {
            // Given a voice with an example, the only Polish one, and a failed sample
            val zosia = VoiceOption(VoiceId("Zosia"), "pl_PL", "Witaj, nazywam się Zosia.")
            val draft = draftOf(NotificationSettings())
            val state =
                NotificationDialogState(
                    draft = draft.copy(speech = draft.speech.copy(voice = zosia.id)),
                    invalid = null,
                    saveError = null,
                    voices = listOf(zosia),
                    previewFailed = true,
                )

            // When the notes are shown
            setContent { AmTestTheme { Column { VoiceNotes(state) } } }

            // Then the example and the hint are drawn in muted, the failure in err
            (muted in pixels(SettingsTags.SPEECH_SAMPLE)) shouldBe true
            (muted in pixels(SettingsTags.POLISH_HINT)) shouldBe true
            (err in pixels(SettingsTags.PREVIEW_FAILED)) shouldBe true
            (err in pixels(SettingsTags.SPEECH_SAMPLE)) shouldBe false
            (muted in pixels(SettingsTags.PREVIEW_FAILED)) shouldBe false
        }
}
