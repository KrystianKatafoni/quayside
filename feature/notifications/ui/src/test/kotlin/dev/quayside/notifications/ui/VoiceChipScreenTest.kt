package dev.quayside.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.SpeechSettings
import dev.quayside.notifications.api.VoiceId
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The voice chip of the top bar (AM-816): the voice name, "Głos systemowy" or "Głos wył."; a button without focus. */
@OptIn(ExperimentalTestApi::class)
internal class VoiceChipScreenTest {
    private val colors = SampleTokens.colors()
    private var clicks = 0

    private fun named(name: String) = NotificationSettings(speech = SpeechSettings(voice = VoiceId(name)))

    private fun androidx.compose.ui.test.ComposeUiTest.show(settings: NotificationSettings) =
        setContent {
            AmTestTheme {
                Box(Modifier.testTag(BOX).background(colors.surfaces.bg.toColor())) {
                    VoiceChip(settings, onClick = { clicks++ }, Modifier.testTag(SettingsTags.VOICE_CHIP))
                }
            }
        }

    private fun androidx.compose.ui.test.ComposeUiTest.has(text: String): Boolean =
        onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `voice chip shows voice name`() =
        runComposeUiTest {
            show(named("Zosia"))

            has("Zosia") shouldBe true
            has("Głos wył.") shouldBe false
        }

    @Test
    fun `voice label falls back to system voice`() =
        runComposeUiTest {
            show(NotificationSettings(speech = SpeechSettings(voice = null)))

            has("Głos systemowy") shouldBe true
        }

    @Test
    fun `voice chip shows off when voice is off`() =
        runComposeUiTest {
            // Given a voice that is chosen but switched off
            show(named("Zosia").copy(voice = false))

            // Then the chip says it is off and does not name the voice
            has("Głos wył.") shouldBe true
            has("Zosia") shouldBe false
        }

    @Test
    fun `voice chip is drawn on raised`() =
        runComposeUiTest {
            show(named("Zosia"))

            val pixels = onNodeWithTag(BOX).captureToImage().toPixelMap()
            val shown =
                (0 until pixels.width).flatMap { x ->
                    (0 until pixels.height).map { y -> pixels[x, y].toArgb() }
                }

            shown shouldContain
                colors.surfaces.raised
                    .toColor()
                    .toArgb()
        }

    @Test
    fun `voice chip click calls onClick`() =
        runComposeUiTest {
            show(named("Zosia"))

            onNodeWithTag(SettingsTags.VOICE_CHIP).performClick()

            clicks shouldBe 1
        }

    @Test
    fun `voice chip never takes focus`() =
        runComposeUiTest {
            show(named("Zosia"))

            onNodeWithTag(SettingsTags.VOICE_CHIP).performClick()
            waitForIdle()

            onNodeWithTag(SettingsTags.VOICE_CHIP).assertIsNotFocused()
        }

    @Test
    fun `space after clicking the voice chip reaches the window handler`() =
        runComposeUiTest {
            var spaces = 0
            setContent {
                AmTestTheme {
                    Box(
                        Modifier
                            .testTag(WINDOW)
                            .onKeyEvent {
                                (it.key == Key.Spacebar && it.type == KeyEventType.KeyDown).also { hit ->
                                    if (hit) spaces++
                                }
                            }.focusable(),
                    ) {
                        VoiceChip(named("Zosia"), onClick = { clicks++ }, Modifier.testTag(SettingsTags.VOICE_CHIP))
                    }
                }
            }
            onNodeWithTag(WINDOW).performSemanticsAction(SemanticsActions.RequestFocus)

            onNodeWithTag(SettingsTags.VOICE_CHIP).performClick()
            onRoot().performKeyInput {
                keyDown(Key.Spacebar)
                keyUp(Key.Spacebar)
            }
            waitForIdle()

            spaces shouldBe 1
            clicks shouldBe 1
            onNodeWithTag(WINDOW).assertIsFocused()
        }

    private companion object {
        const val BOX = "box"
        const val WINDOW = "window"
    }
}
