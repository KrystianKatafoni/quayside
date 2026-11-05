package dev.quayside.terminal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.testing.RecordingLogger
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

private const val WAIT_MS = 5_000L

/** Icon buttons of the tab bar: close, add and "Tryb terminala" (plan AM-809). */
@OptIn(ExperimentalTestApi::class)
class TabBarButtonsTest {
    @TempDir
    lateinit var root: Path

    private val api = ScriptedTerminalApi()
    private var fullScreens = 0

    private fun ComposeUiTest.showArea(onFullScreen: (() -> Unit)?): TerminalController {
        val built = mutableListOf<TerminalController>()
        setContent {
            AmTestTheme {
                val scope = rememberCoroutineScope()
                val controller =
                    remember {
                        TerminalController(
                            api,
                            scope,
                            RecordingLogger(),
                            FixedShellStart(),
                        ).also { built += it }
                    }
                TerminalArea(
                    controller,
                    onAppKey = { false },
                    paneContent = { Box(Modifier.testTag("content")) },
                    onFullScreen = onFullScreen,
                )
            }
        }
        waitUntil(timeoutMillis = WAIT_MS) { built.isNotEmpty() }
        return built.single()
    }

    private fun ComposeUiTest.openShell(controller: TerminalController): TerminalProject {
        val alpha = project("alpha", Files.createDirectory(root.resolve("Alpha")), "Alpha")
        runOnIdle { controller.openShell(alpha) }
        waitUntilExactlyOneExists(hasTestTag(TerminalTags.tab(tabId(1))), WAIT_MS)
        return alpha
    }

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag), WAIT_MS)
        return onNodeWithTag(tag)
    }

    @Test
    fun `close and add are labelled icon buttons that never take focus`() =
        runComposeUiTest {
            // Given a tab
            val controller = showArea(onFullScreen = null)
            val alpha = openShell(controller)

            // Then both buttons are announced by a text, not by a glyph
            await(TerminalTags.tabClose(tabId(1))).assertContentDescriptionEquals(TerminalStrings["tab.close"])
            await(TerminalTags.groupAdd(alpha.id)).assertContentDescriptionEquals(TerminalStrings["group.add"])

            // When the user clicks "+" and moves the focus with Tab, then nothing in the bar has the focus
            // (the click on "x" would remove its node, so the focus is checked through "+" and Tab)
            await(TerminalTags.groupAdd(alpha.id)).performClick()
            waitUntilExactlyOneExists(hasTestTag(TerminalTags.tab(tabId(2))), WAIT_MS)
            await(TerminalTags.groupAdd(alpha.id)).assertIsNotFocused()
            onRoot().performKeyInput {
                keyDown(Key.Tab)
                keyUp(Key.Tab)
            }
            waitForIdle()
            await(TerminalTags.tabClose(tabId(1))).assertIsNotFocused()
            await(TerminalTags.tabClose(tabId(2))).assertIsNotFocused()
            onAllNodes(isFocused()).assertCountEquals(0)
        }

    @Test
    fun `full screen button calls onFullScreen`() =
        runComposeUiTest {
            val controller = showArea(onFullScreen = { fullScreens++ })
            openShell(controller)

            val button = await(TerminalTags.FULL_SCREEN)
            button.assertContentDescriptionEquals("Tryb terminala")
            button.performClick()

            fullScreens shouldBe 1
            button.assertIsNotFocused()
        }

    @Test
    fun `no full screen button without onFullScreen`() =
        runComposeUiTest {
            val controller = showArea(onFullScreen = null)
            openShell(controller)

            onNodeWithTag(TerminalTags.FULL_SCREEN).assertDoesNotExist()
        }
}
