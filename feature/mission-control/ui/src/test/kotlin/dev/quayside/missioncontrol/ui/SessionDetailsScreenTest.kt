package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The details panel (plan AM-809): state chip, button hierarchy, key caps and focus. */
@OptIn(ExperimentalTestApi::class)
internal class SessionDetailsScreenTest {
    private val colors = SampleTokens.colors()
    private val caps = SessionKeyCaps(attach = "⏎", reply = "r", stop = "x", next = "j", previous = "k", preview = "␣")

    private fun waiting() = fakeSessions(worldSession("one", SessionState.AwaitingInput("q"), name = "fix-login"))

    private fun ComposeUiTest.colorsOf(tag: String): Set<Int> = onNodeWithTag(tag).argbColors()

    @Test
    fun `details show status chip with state and age`() =
        runComposeUiTest {
            // Given a session that waits and was last active 4 minutes ago (clock at T0 + 240 s)
            showMc(waiting()).pickSession("one")

            // Then the chip reads "state · age"
            val expected = "${mcText("state.awaitingInput")} · ${mcText("age.min", "4")}"
            awaitTag(MissionControlTags.DETAILS_STATE).assertTextEquals(expected)
        }

    @Test
    fun `details reply is primary and stop is danger`() =
        runComposeUiTest {
            // Given a waiting, active session in the plex theme
            showMc(waiting()).pickSession("one")
            awaitTag(MissionControlTags.DETAILS_REPLY)
            awaitTag(MissionControlTags.DETAILS_ATTACH)
            awaitTag(MissionControlTags.DETAILS_STOP)

            // Then Odpowiedz is filled with accent, Attach is an ordinary button on raised, and Stop is an err
            // outline with neither of those fills
            val accent =
                colors.content.accent
                    .toColor()
                    .toArgb()
            val raised =
                colors.surfaces.raised
                    .toColor()
                    .toArgb()
            val err =
                colors.status.err
                    .toColor()
                    .toArgb()
            colorsOf(MissionControlTags.DETAILS_REPLY) shouldContain accent
            colorsOf(MissionControlTags.DETAILS_ATTACH) shouldContain raised
            colorsOf(MissionControlTags.DETAILS_ATTACH) shouldNotContain accent
            val stop = colorsOf(MissionControlTags.DETAILS_STOP)
            stop shouldContain err
            stop shouldNotContain accent
            stop shouldNotContain raised
        }

    @Test
    fun `details buttons show key caps from handlers`() =
        runComposeUiTest {
            showMc(waiting(), MissionControlHandlers(keyCaps = caps)).pickSession("one")

            awaitTag(MissionControlTags.DETAILS_ATTACH).assertTextEquals(mcText("action.attach"), "⏎")
            awaitTag(MissionControlTags.DETAILS_REPLY).assertTextEquals(mcText("action.reply"), "r")
            awaitTag(MissionControlTags.DETAILS_STOP).assertTextEquals(mcText("action.stop"), "x")
        }

    @Test
    fun `details without key caps show no shortcut`() =
        runComposeUiTest {
            showMc(waiting()).pickSession("one")

            // The label alone: the shortcut is not part of the text
            awaitTag(MissionControlTags.DETAILS_ATTACH).assertTextEquals("Attach")
            awaitTag(MissionControlTags.DETAILS_REPLY).assertTextEquals("Odpowiedz")
            awaitTag(MissionControlTags.DETAILS_STOP).assertTextEquals("Zatrzymaj")
        }

    @Test
    fun `space after clicking a details button reaches the window handler`() =
        runComposeUiTest {
            // Given a window handler with focus around Mission Control
            var spaces = 0
            var attaches = 0
            showMc(waiting()) { controller ->
                Box(
                    Modifier
                        .testTag(WINDOW)
                        .onKeyEvent {
                            (it.key == Key.Spacebar && it.type == KeyEventType.KeyDown).also { hit ->
                                if (hit) spaces++
                            }
                        }.focusable(),
                ) {
                    MissionControl(controller, handlers = MissionControlHandlers(onAttach = { attaches++ }))
                }
            }.pickSession("one")
            onNodeWithTag(WINDOW).performSemanticsAction(SemanticsActions.RequestFocus)

            // When the user clicks Attach and presses space
            awaitTag(MissionControlTags.DETAILS_ATTACH).performClick()
            onRoot().performKeyInput {
                keyDown(Key.Spacebar)
                keyUp(Key.Spacebar)
            }
            waitForIdle()

            // Then the window handler gets the space, and Attach was not clicked again
            spaces shouldBe 1
            attaches shouldBe 1
            onNodeWithTag(WINDOW).assertIsFocused()
        }

    private companion object {
        const val WINDOW = "window"
    }
}
