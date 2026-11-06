package dev.quayside.notifications.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.FakeVoiceCatalog
import dev.quayside.notifications.api.FakeVoicePreview
import dev.quayside.notifications.api.NotificationSettings
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test

private const val WAIT_MS = 5_000L

/** The colours of the captions of the quiet hours section (NFR-6, AM-706): muted for the notes, err for a refusal. */
@OptIn(ExperimentalTestApi::class)
class QuietNotesColorTest {
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
    fun `quiet notes use the muted colour`() =
        runComposeUiTest {
            // Given a state where the start of the hours was refused
            val state =
                NotificationDialogState(
                    draft = draftOf(NotificationSettings()),
                    invalid = InvalidField(NotificationField.QUIET_FROM),
                    saveError = null,
                )

            // When the section is shown
            setContent {
                AmTestTheme {
                    val scope = rememberCoroutineScope()
                    val controller =
                        remember {
                            NotificationSettingsController(
                                FakeNotificationSettingsRepo(),
                                FakeVoiceCatalog(emptyList()),
                                FakeVoicePreview(),
                                MutableStateFlow(emptyList()),
                                scope,
                            )
                        }
                    Column { QuietSection(state, controller) }
                }
            }

            // Then the hint and the Focus note are drawn in muted, the refusal in err
            (muted in pixels(SettingsTags.QUIET_HINT)) shouldBe true
            (muted in pixels(SettingsTags.FOCUS_NOTE)) shouldBe true
            (err in pixels(SettingsTags.invalid(NotificationField.QUIET_FROM))) shouldBe true
            (err in pixels(SettingsTags.QUIET_HINT)) shouldBe false
            (err in pixels(SettingsTags.FOCUS_NOTE)) shouldBe false
            (muted in pixels(SettingsTags.invalid(NotificationField.QUIET_FROM))) shouldBe false
        }
}
