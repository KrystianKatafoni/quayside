package dev.quayside.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * FR-T2 in the whole window on fakes (plan AM-819): the handle between Mission Control and the terminal panel,
 * its limits, the window size, the remembered height and the window keys. Heights come from the layout bounds; the
 * test window has density 1, so pixels of the mouse equal dp. The injected drag may lose the touch slop of the
 * first move, so exact amounts are asserted with a margin of [SLOP] dp.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("ForbiddenMethodCall") // runBlocking is allowed in tests (ENG-C3).
class TerminalSplitWindowTest {
    @TempDir
    lateinit var root: Path

    private val terminals = RecordingTerminals()
    private val agent = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")
    private val sessions =
        FakeSessions(
            SessionsWorld(
                listOf(WorldProject(agent.id, agent.dir)),
                listOf(
                    WorldSession(SessionId("older-work"), A_PROJECT_DIR, SessionState.Working, "older-work", T0),
                    WorldSession(
                        SessionId("newer-work"),
                        A_PROJECT_DIR,
                        SessionState.Working,
                        "newer-work",
                        T0.plusSeconds(60),
                    ),
                ),
            ),
        )

    private lateinit var setup: WindowSetup

    private val file get() = root.resolve(WINDOW_FILE)

    private fun attachCommand(id: SessionId) = ShellCommand(listOf("claude", "attach", id.value), root)

    private fun ComposeUiTest.window(initial: WindowLayout = WindowLayout()) {
        setup = WindowSetup(FakeProjectsApi(initial = listOf(agent)), sessions, terminals, ::attachCommand)
        setup.panes.storeOn(root)
        setup.panes.initial = initial
        showWindowOf(setup)
        await(WindowTags.projectRow("project0001")).performClick()
    }

    /** Selects the newest session and attaches it with Enter: the list mode with one terminal tab. */
    private fun ComposeUiTest.attached() {
        await(WindowTags.missionRow("newer-work")).performClick()
        await(WindowTags.DETAILS_PREVIEW)
        waitForIdle()
        press(Key.Enter)
        await(WindowTags.terminalTab(1))
        waitForIdle()
    }

    private fun ComposeUiTest.press(key: Key) {
        onNodeWithTag(WindowTags.KEYS).performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.has(tag: String): Boolean = onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.terminalHeight(): Float =
        onNodeWithTag(WindowTags.TERMINAL_AREA).getBoundsInRoot().height.value

    private fun ComposeUiTest.missionHeight(): Float =
        onNodeWithTag(WindowTags.MISSION_CONTROL).getBoundsInRoot().height.value

    /** Drags the handle by [dy] px (negative = up) with the mouse and lets go. */
    private fun ComposeUiTest.drag(dy: Float) {
        onNodeWithTag(SplitTags.HANDLE).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(0f, dy))
            release()
        }
        waitForIdle()
    }

    private fun ComposeUiTest.resizeWindow(height: Float?) {
        runOnIdle { setup.panes.windowHeight.value = height?.dp }
        waitForIdle()
    }

    private fun ComposeUiTest.detailsShow(name: String) =
        waitUntil {
            onAllNodes(hasText(name, substring = true) and hasAnyAncestor(hasTestTag(WindowTags.DETAILS)))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

    @Test
    fun `FR-T2 handle is hidden without terminal tabs`() =
        runComposeUiTest {
            // Given a window with no terminal tab
            window()

            // Then there is no handle and Mission Control keeps the whole height
            await(WindowTags.MISSION_CONTROL)
            has(SplitTags.HANDLE) shouldBe false

            // When a tab opens, the handle appears
            attached()
            has(SplitTags.HANDLE) shouldBe true
        }

    @Test
    fun `FR-T2 dragging the handle up makes the terminal taller`() =
        runComposeUiTest {
            // Given a terminal panel under Mission Control
            window()
            attached()
            val before = terminalHeight()
            val missionBefore = missionHeight()

            // When the user drags the handle up by 100 dp
            drag(dy = -100f)

            // Then the terminal is taller by about 100 dp and Mission Control shorter by as much
            terminalHeight() shouldBeGreaterThan before + 100f - SLOP
            terminalHeight() shouldBeLessThan before + 100f + 1f
            missionHeight() shouldBeLessThan missionBefore - 100f + SLOP
        }

    @Test
    fun `FR-T2 dragging the handle down makes the terminal shorter`() =
        runComposeUiTest {
            window()
            attached()
            val before = terminalHeight()

            drag(dy = 100f)

            terminalHeight() shouldBeLessThan before - 100f + SLOP
            terminalHeight() shouldBeGreaterThan before - 100f - 1f
        }

    @Test
    fun `FR-T2 dragging down stops at the minimum terminal height`() =
        runComposeUiTest {
            window()
            attached()

            drag(dy = 2000f)

            terminalHeight() shouldBe (MIN_TERMINAL_HEIGHT.value plusOrMinus 1f)
        }

    @Test
    fun `FR-T2 dragging up stops at the minimum Mission Control height`() =
        runComposeUiTest {
            window()
            attached()

            drag(dy = -2000f)

            // Mission Control keeps 160 dp (the 6 dp handle may come on top of it)
            missionHeight() shouldBeGreaterThan MIN_MISSION_HEIGHT.value - 1f
            missionHeight() shouldBeLessThan MIN_MISSION_HEIGHT.value + HANDLE_DP + 1f
        }

    @Test
    fun `FR-T2 the saved height after a drag past the limit is the shown height`() =
        runComposeUiTest {
            window()
            attached()

            drag(dy = -2000f)

            // The limit leaves Mission Control 160 dp without the handle: the saved height is the shown one
            val saved = runBlocking { layoutStoreOn(root).load() }.terminalHeight
            saved shouldBe (terminalHeight() plusOrMinus 1f)
        }

    @Test
    fun `FR-T2 dragged height is saved when the drag ends`() =
        runComposeUiTest {
            window()
            attached()
            file.exists() shouldBe false

            drag(dy = -100f)

            val saved = runBlocking { layoutStoreOn(root).load() }.terminalHeight
            saved shouldBeGreaterThan DEFAULT_TERMINAL_HEIGHT.value + 100f - SLOP
            saved shouldBeLessThan DEFAULT_TERMINAL_HEIGHT.value + 100f + 1f
        }

    @Test
    fun `FR-T2 dragged height comes back in a new window`() {
        // Given a dragged height, saved at the end of the drag, in the first window
        var dragged = 0f
        runComposeUiTest {
            window()
            attached()
            drag(dy = -100f)
            dragged = terminalHeight()
        }

        // When the window is opened again with the layout that main reads at start
        val layout = runBlocking { layoutStoreOn(root).load() }
        runComposeUiTest {
            window(initial = layout)
            attached()

            // Then the panel has the dragged height at once
            terminalHeight() shouldBe (dragged plusOrMinus 1f)
        }
    }

    @Test
    fun `FR-T2 smaller window shrinks the terminal and a bigger one restores it`() =
        runComposeUiTest {
            // Given a terminal of the default height
            window()
            attached()
            val before = terminalHeight()

            // When the window gets much lower
            resizeWindow(height = 400f)

            // Then the terminal shrinks but keeps its minimum, and nothing is saved
            terminalHeight() shouldBeLessThan before - 100f
            terminalHeight() shouldBeGreaterThan MIN_TERMINAL_HEIGHT.value - 1f
            file.exists() shouldBe false

            // When the window gets its height back, the remembered height comes back
            resizeWindow(height = null)
            terminalHeight() shouldBe (before plusOrMinus 1f)
        }

    @Test
    fun `FR-T2 a drag in a smaller window starts from the shown height`() =
        runComposeUiTest {
            // Given a remembered 500 dp in a window where less is shown
            window(initial = WindowLayout(terminalHeight = 500f))
            attached()
            resizeWindow(height = 500f)
            val shown = terminalHeight()

            // When the user drags down by 20 dp (up would stop at the Mission Control minimum: shown is the maximum)
            drag(dy = 20f)

            // Then the terminal shrinks from the shown height; from the remembered 500 dp it would not move
            terminalHeight() shouldBeLessThan shown - 20f + SLOP
            terminalHeight() shouldBeGreaterThan shown - 20f - 1f
        }

    @Test
    fun `FR-T2 dragging the handle keeps the window keys`() =
        runComposeUiTest {
            // Given an attached session (newer-work selected) and a dragged handle
            window()
            attached()
            drag(dy = -50f)

            // Then the keys of the window still have the focus and the handle has not taken it
            onNodeWithTag(WindowTags.KEYS).assertIsFocused()
            onNodeWithTag(SplitTags.HANDLE).assertIsNotFocused()

            // When the user presses j, the selection moves to the next session
            press(Key.J)
            detailsShow("older-work")
        }

    @Test
    fun `FR-T2 Enter after a drag still attaches and does not click the handle`() =
        runComposeUiTest {
            window()
            attached()
            drag(dy = -50f)

            press(Key.J)
            detailsShow("older-work")
            press(Key.Enter)

            await(WindowTags.terminalTab(2)).assertTextContains("older-work", substring = true)
        }

    @Test
    fun `FR-T2 Tab never moves the focus to the handle`() =
        runComposeUiTest {
            // Given an attached session and a focused window
            window()
            attached()
            onNodeWithTag(WindowTags.KEYS).assertIsFocused()

            // When the user walks through the focus order with Tab
            repeat(times = TAB_PRESSES) {
                press(Key.Tab)

                // Then the handle never takes the focus
                onNodeWithTag(SplitTags.HANDLE).assertIsNotFocused()
            }
        }

    @Test
    fun `FR-T2 list mode after terminal mode shows the dragged height`() =
        runComposeUiTest {
            // Given a dragged height
            window()
            attached()
            drag(dy = -100f)
            val dragged = terminalHeight()

            // When the user goes to the terminal mode and back
            await(WindowTags.FULL_SCREEN).performClick()
            await(WindowTags.RAIL)
            await(ModeTags.BACK).performClick()
            await(WindowTags.SIDEBAR)
            waitForIdle()

            // Then the panel has the dragged height again
            terminalHeight() shouldBe (dragged plusOrMinus 1f)
        }

    private companion object {
        /** Margin for the touch slop the injected drag may swallow. */
        const val SLOP = 20f
        const val HANDLE_DP = 6f
        const val TAB_PRESSES = 25
    }
}
