package dev.agentmanager.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.agentmanager.commandpalette.ui.CommandPaletteController
import dev.agentmanager.projects.api.FakeProjectsApi
import dev.agentmanager.projects.api.aProject
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** User scenarios of the terminal panel in the whole window on fakes (FR-T2; AM-503). */
@OptIn(ExperimentalTestApi::class)
class TerminalWindowTest {
    @TempDir
    lateinit var root: Path

    private val terminals = RecordingTerminals()

    private val agentDir by lazy { Files.createDirectory(root.resolve("agent")) }
    private val otherDir by lazy { Files.createDirectory(root.resolve("other")) }
    private val agent by lazy { aProject(id = "project0001", dir = agentDir, displayName = "Agent Manager") }
    private val other by lazy { aProject(id = "project0002", dir = otherDir, displayName = "Other") }

    private fun ComposeUiTest.window(): CommandPaletteController =
        showWindow(FakeProjectsApi(initial = listOf(agent, other)), terminal = terminals)

    private fun ComposeUiTest.select(project: String) {
        await(WindowTags.projectRow(project)).performClick()
    }

    /** Title keys of the terminal commands; the palette list is clipped, so rows are found by typing the title. */
    private val titleKeys =
        mapOf(
            "terminal.newShell" to "command.newShell",
            "terminal.splitSideBySide" to "command.splitSideBySide",
            "terminal.splitStacked" to "command.splitStacked",
            "terminal.nextPane" to "command.nextPane",
            "terminal.maximize" to "command.maximizePane",
            "terminal.restore" to "command.restorePanes",
        )

    private fun titleOf(id: String): String = Strings[titleKeys.getValue(id)]

    /** Opens the palette, narrows it to the command [id] by its title and picks it. */
    private fun ComposeUiTest.runCommand(
        palette: CommandPaletteController,
        id: String,
    ) {
        runOnIdle { palette.open() }
        await(WindowTags.PALETTE_FIELD).performTextInput(titleOf(id))
        await(WindowTags.paletteRow(id)).performClick()
        waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE))
    }

    /** Narrows the palette to each command by its title: an offered command would show as a row after the filter. */
    private fun ComposeUiTest.assertNotOffered(
        palette: CommandPaletteController,
        vararg ids: String,
    ) {
        ids.forEach { id ->
            runOnIdle { palette.open() }
            await(WindowTags.PALETTE_FIELD).performTextInput(titleOf(id))
            waitForIdle()
            onNodeWithTag(WindowTags.paletteRow(id)).assertDoesNotExist()
            runOnIdle { palette.close() }
            waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE))
        }
    }

    @Test
    fun `new shell command opens a tab in the selected project dir`() =
        runComposeUiTest {
            // Given a selected project
            val palette = window()
            select("project0001")

            // When the user runs "New shell tab"
            runCommand(palette, "terminal.newShell")

            // Then a shell starts in the project dir and its tab shows in the project's group
            await(WindowTags.terminalTab(1)).assertTextContains("zsh", substring = true)
            await(WindowTags.terminalGroup("project0001")).assertTextContains("Agent Manager", substring = true)
            terminals.shellDirs shouldBe listOf(agentDir)
        }

    @Test
    fun `new shell command is not offered without a selected project`() =
        runComposeUiTest {
            // Given a window with projects but no selection
            val palette = window()
            await(WindowTags.projectRow("project0001"))

            // Then the palette lacks the command
            assertNotOffered(palette, "terminal.newShell")
        }

    @Test
    fun `tabs of two projects are grouped in the bar`() =
        runComposeUiTest {
            // Given a shell in the first project
            val palette = window()
            select("project0001")
            runCommand(palette, "terminal.newShell")
            await(WindowTags.terminalTab(1))

            // When the user selects the second project and opens a shell there
            select("project0002")
            runCommand(palette, "terminal.newShell")

            // Then the bar has a group per project
            await(WindowTags.terminalTab(2))
            await(WindowTags.terminalGroup("project0001")).assertTextContains("Agent Manager", substring = true)
            await(WindowTags.terminalGroup("project0002")).assertTextContains("Other", substring = true)
            terminals.shellDirs shouldBe listOf(agentDir, otherDir)
        }

    @Test
    fun `terminal panel appears under mission control with the first tab`() =
        runComposeUiTest {
            // Given a window without tabs: no panel
            val palette = window()
            select("project0001")
            onNodeWithTag(WindowTags.TERMINAL_AREA).assertDoesNotExist()

            // When the first shell opens
            runCommand(palette, "terminal.newShell")

            // Then the panel shows with the tab and the shell
            await(WindowTags.TERMINAL_AREA)
            await(WindowTags.terminalTab(1))
            await(WindowTags.terminalContent("fake-1"))
        }

    @Test
    fun `closing the last tab hides the panel`() =
        runComposeUiTest {
            // Given a panel with one tab
            val palette = window()
            select("project0001")
            runCommand(palette, "terminal.newShell")
            await(WindowTags.TERMINAL_AREA)

            // When the user closes the tab
            await(WindowTags.terminalTabClose(1)).performClick()

            // Then the panel is gone and the session is closed
            waitUntilDoesNotExist(hasTestTag(WindowTags.TERMINAL_AREA))
            terminals.fake.sessions
                .single()
                .isClosed shouldBe true
        }

    @Test
    fun `split command opens a second pane with a shell in the same project`() =
        runComposeUiTest {
            // Given a shell in the selected project
            val palette = window()
            select("project0001")
            runCommand(palette, "terminal.newShell")
            await(WindowTags.terminalPane(1))

            // When the user splits the panel
            runCommand(palette, "terminal.splitSideBySide")

            // Then there are two panes and the new shell runs in the same dir
            await(WindowTags.terminalPane(2))
            await(WindowTags.terminalPane(1))
            terminals.shellDirs shouldBe listOf(agentDir, agentDir)
        }

    @Test
    fun `split commands are not offered with four panes`() =
        runComposeUiTest {
            // Given four panes
            val palette = window()
            select("project0001")
            runCommand(palette, "terminal.newShell")
            runCommand(palette, "terminal.splitSideBySide")
            runCommand(palette, "terminal.splitStacked")
            runCommand(palette, "terminal.splitSideBySide")
            await(WindowTags.terminalPane(4))

            // Then neither split command is offered
            assertNotOffered(palette, "terminal.splitSideBySide", "terminal.splitStacked")
        }

    @Test
    fun `next pane command moves the accent frame`() =
        runComposeUiTest {
            // Given two panes, the new one active
            val palette = window()
            select("project0001")
            runCommand(palette, "terminal.newShell")
            runCommand(palette, "terminal.splitSideBySide")
            await(WindowTags.terminalPane(2)).assertIsSelected()
            await(WindowTags.terminalPane(1)).assertIsNotSelected()

            // When the user goes to the next pane (cyclic)
            runCommand(palette, "terminal.nextPane")

            // Then the frame is on the first pane
            await(WindowTags.terminalPane(1)).assertIsSelected()
            await(WindowTags.terminalPane(2)).assertIsNotSelected()
        }

    @Test
    fun `maximize command shows one pane and restore brings both back`() =
        runComposeUiTest {
            // Given two panes
            val palette = window()
            select("project0001")
            runCommand(palette, "terminal.newShell")
            runCommand(palette, "terminal.splitSideBySide")
            await(WindowTags.terminalPane(2))

            // When the user maximizes the active pane
            runCommand(palette, "terminal.maximize")

            // Then only it is shown
            waitUntilDoesNotExist(hasTestTag(WindowTags.terminalPane(1)))
            await(WindowTags.terminalPane(2))

            // And restoring brings both back
            runCommand(palette, "terminal.restore")
            await(WindowTags.terminalPane(1))
            await(WindowTags.terminalPane(2))
        }
}
