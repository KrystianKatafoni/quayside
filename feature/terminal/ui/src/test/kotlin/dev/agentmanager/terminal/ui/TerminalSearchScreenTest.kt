package dev.agentmanager.terminal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.agentmanager.core.testing.RecordingLogger
import dev.agentmanager.terminal.api.FakeTerminalSession
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Cold JVM start-up can exceed the default 1 s of `waitUntil` in the first test; see TerminalAreaScreenTest. */
private const val WAIT_MS = 5_000L

/** Scenarios of the search bar (FR-T3) on fake terminals; widgets are replaced by markers. */
@OptIn(ExperimentalTestApi::class)
class TerminalSearchScreenTest {
    @TempDir
    lateinit var root: Path

    private val api = ScriptedTerminalApi()

    /** Shows the area over [api]; each pane draws a plain marker instead of a widget. */
    private fun ComposeUiTest.showArea(): TerminalController {
        val built = mutableListOf<TerminalController>()
        setContent {
            val scope = rememberCoroutineScope()
            val controller = remember { TerminalController(api, scope, RecordingLogger(), "zsh").also { built += it } }
            TerminalArea(controller, onAppKey = { false }, paneContent = { Box(Modifier.testTag("content")) })
        }
        waitUntil(timeoutMillis = WAIT_MS) { built.isNotEmpty() }
        return built.single()
    }

    /** One shell whose screen shows [lines]. */
    private fun ComposeUiTest.shellShowing(
        controller: TerminalController,
        vararg lines: String,
    ): FakeTerminalSession {
        val project = project("alpha", Files.createDirectory(root.resolve("Alpha")), "Alpha")
        runOnIdle { controller.openShell(project) }
        waitUntil(timeoutMillis = WAIT_MS) {
            controller.state.value.tabs
                .isNotEmpty() && api.fake.sessions.isNotEmpty()
        }
        val session = api.fake.sessions.first()
        lines.forEach(session::print)
        return session
    }

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag), WAIT_MS)
        return onNodeWithTag(tag)
    }

    private fun ComposeUiTest.awaitCounter(text: String) {
        waitUntilExactlyOneExists(hasTestTag(TerminalTags.SEARCH_COUNTER) and hasText(text), WAIT_MS)
    }

    /** The user presses ⌘F and the bar opens. */
    private fun ComposeUiTest.openBar(controller: TerminalController) {
        runOnIdle { controller.perform(PaneCommand.Find) }
        await(TerminalTags.SEARCH_BAR)
    }

    private fun ComposeUiTest.typeInBar(text: String) {
        await(TerminalTags.SEARCH_FIELD).performTextInput(text)
    }

    @Test
    fun `FR-T3 find command shows the bar in the active pane`() =
        runComposeUiTest {
            // Given two panes; the second is active
            val controller = showArea()
            shellShowing(controller, "hello")
            runOnIdle { controller.split(SplitAxis.SIDE_BY_SIDE) }
            waitUntil(timeoutMillis = WAIT_MS) { controller.state.value.tabs.size == 2 }
            onNodeWithTag(TerminalTags.SEARCH_BAR).assertDoesNotExist()

            // When the user presses the find shortcut
            runOnIdle { controller.perform(PaneCommand.Find) }

            // Then one bar shows, inside the active pane only
            await(TerminalTags.SEARCH_BAR)
            val activePane = hasAnyAncestor(hasTestTag(TerminalTags.pane(pane(2))))
            waitUntilExactlyOneExists(hasTestTag(TerminalTags.SEARCH_BAR) and activePane, WAIT_MS)
        }

    @Test
    fun `FR-T3 typing shows the hit counter`() =
        runComposeUiTest {
            // Given a bar over a screen with three hits
            val controller = showArea()
            shellShowing(controller, "error one", "fine", "Error two", "ERROR three")
            openBar(controller)

            // When the user types
            typeInBar("error")

            // Then the newest hit is selected: 3/3
            awaitCounter("3/3")
        }

    @Test
    fun `FR-T3 Enter moves to the older hit and Shift Enter to the newer`() =
        runComposeUiTest {
            // Given three hits with the newest selected
            val controller = showArea()
            shellShowing(controller, "hit a", "hit b", "hit c")
            openBar(controller)
            typeInBar("hit")
            awaitCounter("3/3")

            // When the user presses Enter in the field
            await(TerminalTags.SEARCH_FIELD).performKeyInput { pressKey(Key.Enter) }

            // Then the older hit is selected
            awaitCounter("2/3")

            // And Shift Enter goes back to the newer one
            await(TerminalTags.SEARCH_FIELD).performKeyInput {
                keyDown(Key.ShiftLeft)
                pressKey(Key.Enter)
                keyUp(Key.ShiftLeft)
            }
            awaitCounter("3/3")
        }

    @Test
    fun `FR-T3 Aa toggle makes the search case sensitive`() =
        runComposeUiTest {
            // Given hits of both cases
            val controller = showArea()
            shellShowing(controller, "Error", "no err")
            openBar(controller)
            typeInBar("err")
            awaitCounter("2/2")

            // When the user turns Aa on
            await(TerminalTags.SEARCH_CASE).performClick()

            // Then only the exact case is found
            awaitCounter("1/1")
        }

    @Test
    fun `FR-T3 regex toggle finds digits`() =
        runComposeUiTest {
            // Given a screen with digits; the pattern as plain text finds nothing
            val controller = showArea()
            shellShowing(controller, "a12b3")
            openBar(controller)
            typeInBar("""\d+""")
            await(TerminalTags.SEARCH_NO_HITS)

            // When the user turns .* on
            await(TerminalTags.SEARCH_REGEX).performClick()

            // Then both numbers are found
            awaitCounter("2/2")
        }

    @Test
    fun `FR-T3 invalid regex shows an error`() =
        runComposeUiTest {
            // Given regex mode
            val controller = showArea()
            shellShowing(controller, "a(b")
            openBar(controller)
            await(TerminalTags.SEARCH_REGEX).performClick()

            // When the user types an unfinished group
            typeInBar("(")

            // Then the bar says so and shows no counter
            await(TerminalTags.SEARCH_INVALID).assertTextContains("Błędny wzorzec", substring = true)
            onNodeWithTag(TerminalTags.SEARCH_COUNTER).assertDoesNotExist()
        }

    @Test
    fun `FR-T3 Escape closes the bar and clears the search`() =
        runComposeUiTest {
            // Given hits highlighted in the terminal
            val controller = showArea()
            val session = shellShowing(controller, "hit a", "hit b")
            openBar(controller)
            typeInBar("hit")
            awaitCounter("2/2")
            session.search.highlighted.shouldNotBeEmpty()

            // When the user presses Escape
            await(TerminalTags.SEARCH_FIELD).performKeyInput { pressKey(Key.Escape) }

            // Then the bar is gone and the terminal has no highlights
            waitUntilDoesNotExist(hasTestTag(TerminalTags.SEARCH_BAR), WAIT_MS)
            waitUntil(timeoutMillis = WAIT_MS) { session.search.clears > 0 }
            session.search.highlighted shouldBe emptyList()
        }

    @Test
    fun `FR-T3 no hits shows a fixed text`() =
        runComposeUiTest {
            // Given a bar
            val controller = showArea()
            shellShowing(controller, "hello")
            openBar(controller)

            // When the text has no hits
            typeInBar("zzz")

            // Then a fixed text shows instead of the counter
            await(TerminalTags.SEARCH_NO_HITS).assertTextContains("Brak wyników", substring = true)
            onNodeWithTag(TerminalTags.SEARCH_COUNTER).assertDoesNotExist()
        }
}
