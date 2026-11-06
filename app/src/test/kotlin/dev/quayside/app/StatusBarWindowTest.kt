package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The status bar of the window and the left column on fakes (AM-816, decision 2): the bar belongs to the window, so it
 * is drawn in both layouts; the cheat sheet toggle and "Dodaj projekt z dysku…" are at the bottom of the left column.
 */
@OptIn(ExperimentalTestApi::class)
class StatusBarWindowTest {
    @TempDir
    lateinit var root: Path

    private val world by lazy { TopBarWorld(root) }

    @Test
    fun `status bar is shown in both modes`() =
        runComposeUiTest {
            with(world) {
                window()
                await(WindowTags.MODE_BAR)
                has(WindowTags.RAIL) shouldBe false

                terminalMode()

                // exactly one bar in the terminal mode too (await waits for exactly one node)
                await(WindowTags.MODE_BAR)
                has(WindowTags.RAIL) shouldBe true
            }
        }

    @Test
    fun `status bar says NORMAL in the list and TERMINAL in terminal mode`() =
        runComposeUiTest {
            with(world) {
                window()
                await(WindowTags.MODE_BAR)
                awaitText(WindowTags.MODE_BAR, "NORMAL")
                textIn(WindowTags.MODE_BAR, "TERMINAL") shouldBe false

                terminalMode()

                awaitText(WindowTags.MODE_BAR, "TERMINAL")
                textIn(WindowTags.MODE_BAR, "NORMAL") shouldBe false
            }
        }

    @Test
    fun `status bar stands at the bottom of the window in both modes`() =
        runComposeUiTest {
            with(world) {
                window()
                val list = await(WindowTags.MISSION_CONTROL).fetchSemanticsNode().boundsInRoot
                val bar = await(WindowTags.MODE_BAR).fetchSemanticsNode().boundsInRoot
                list.bottom shouldBeLessThanOrEqualTo bar.top

                terminalMode()

                val terminal = await(WindowTags.TERMINAL_AREA).fetchSemanticsNode().boundsInRoot
                await(WindowTags.MODE_BAR).fetchSemanticsNode().boundsInRoot.top shouldBeGreaterThanOrEqualTo
                    terminal.bottom
            }
        }

    @Test
    fun `status bar shows the project the waiting and working counts and the notification status`() =
        runComposeUiTest {
            with(world) {
                window()
                await(WindowTags.projectRow("project0001")).performClick()

                awaitText(WindowTags.MODE_BAR, "Harbor")
                awaitText(WindowTags.MODE_BAR, "2 czekają")
                awaitText(WindowTags.MODE_BAR, "1 pracuje")
                awaitText(WindowTags.MODE_BAR, "głos: wł. · popup: prawy dół")
            }
        }

    @Test
    fun `status bar shows the supervisor version of Claude Code`() =
        runComposeUiTest {
            with(world) {
                window(supervisor = "2.1.150")

                awaitText(WindowTags.MODE_BAR, "supervisor 2.1.150")
            }
        }

    @Test
    fun `status bar shows the freshness without a version`() =
        runComposeUiTest {
            with(world) {
                window(supervisor = null)

                awaitText(WindowTags.MODE_BAR, "na żywo")
                textIn(WindowTags.MODE_BAR, "supervisor") shouldBe false
            }
        }

    @Test
    fun `shortcuts help sits at the bottom of the left column`() =
        runComposeUiTest {
            with(world) {
                window()
                val sidebar = await(WindowTags.SIDEBAR).fetchSemanticsNode().boundsInRoot
                val failed = await(WindowTags.view("failed")).fetchSemanticsNode().boundsInRoot
                val mission = await(WindowTags.MISSION_CONTROL).fetchSemanticsNode().boundsInRoot

                val toggle = await(WindowTags.SHORTCUTS_TOGGLE).fetchSemanticsNode().boundsInRoot

                // under the global views, in the column of the sidebar, left of Mission Control
                toggle.top shouldBeGreaterThanOrEqualTo failed.bottom
                toggle.left shouldBeGreaterThanOrEqualTo sidebar.left
                toggle.right shouldBeLessThanOrEqualTo mission.left
                textIn(WindowTags.SHORTCUTS_TOGGLE, "? skróty klawiszowe") shouldBe true
                // and the status bar no longer has it
                textIn(WindowTags.MODE_BAR, "skróty") shouldBe false
            }
        }

    @Test
    fun `space after clicking the shortcuts toggle reaches the window`() =
        runComposeUiTest {
            with(world) {
                window()
                await(WindowTags.projectRow("project0001")).performClick()
                await(WindowTags.missionRow("popup1"))

                await(WindowTags.SHORTCUTS_TOGGLE).performClick()
                await(WindowTags.SHORTCUTS)
                press(Key.J)
                press(Key.Spacebar)

                await(WindowTags.PREVIEW)
                onNodeWithTag(WindowTags.SHORTCUTS_TOGGLE).assertIsNotFocused()
            }
        }

    @Test
    fun `sidebar add from disk opens the picker`() =
        runComposeUiTest {
            with(world) {
                window()

                await(WindowTags.ADD_FROM_DISK).performClick()

                waitUntil { setup.picker.picks == 1 }
            }
        }

    @Test
    fun `sidebar add from disk shows the real shortcut`() =
        runComposeUiTest {
            with(world) {
                window()
                await(WindowTags.ADD_FROM_DISK)

                textIn(WindowTags.ADD_FROM_DISK, "Dodaj projekt z dysku…") shouldBe true
                // AppShortcuts.ADD_PROJECT, not the ⌘O of the mockup
                textIn(WindowTags.ADD_FROM_DISK, "⇧⌘O") shouldBe true
            }
        }
}
