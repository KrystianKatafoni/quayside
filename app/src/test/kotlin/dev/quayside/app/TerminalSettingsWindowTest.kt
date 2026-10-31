package dev.quayside.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.quayside.commandpalette.ui.CommandPaletteController
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import dev.quayside.terminal.api.FakeTerminalSettingsRepo
import dev.quayside.terminal.api.ShellProfile
import dev.quayside.terminal.api.StartDirectory
import dev.quayside.terminal.api.TerminalSettings
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * New shell tabs follow the terminal settings in the whole window on fakes (FR-T7, AM-508). The project `Quayside`
 * has the session `fix-login` working in a worktree under the project dir. `optionAsMeta` stays off in every case.
 */
@OptIn(ExperimentalTestApi::class)
class TerminalSettingsWindowTest {
    @TempDir
    lateinit var root: Path

    private val terminals = RecordingTerminals()
    private val projectDir by lazy { Files.createDirectory(root.resolve("quayside")) }
    private val worktree by lazy { Files.createDirectories(projectDir.resolve(".claude/worktrees/fix-login")) }
    private val project by lazy { aProject(id = "project0001", dir = projectDir, displayName = "Quayside") }

    private fun sessions(): FakeSessions =
        FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(project.id, project.dir)),
                sessions =
                    listOf(
                        WorldSession(
                            id = SessionId("fix-login"),
                            cwd = worktree,
                            name = "fix-login",
                            startedAt = T0,
                        ),
                    ),
            ),
        )

    private fun ComposeUiTest.window(settings: TerminalSettings): CommandPaletteController =
        showWindow(
            FakeProjectsApi(initial = listOf(project)),
            sessions(),
            terminals,
            terminalSettings = FakeTerminalSettingsRepo(settings),
        )

    /** Given the project and its session `fix-login` selected. */
    private fun ComposeUiTest.selectSession() {
        await(WindowTags.projectRow("project0001")).performClick()
        await(WindowTags.missionRow("fix-login")).performClick()
        await(WindowTags.DETAILS_PREVIEW)
        waitForIdle()
    }

    private fun ComposeUiTest.newShell(palette: CommandPaletteController) {
        runOnIdle { palette.open() }
        await(WindowTags.PALETTE_FIELD).performTextInput(Strings["command.newShell"])
        await(WindowTags.paletteRow("terminal.newShell")).performClick()
        waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE))
    }

    @Test
    fun `FR-T7 new shell starts in the selected session worktree`() =
        runComposeUiTest {
            // Given the default settings and a selected session that works in a worktree
            val palette = window(TerminalSettings())
            selectSession()

            // When the user opens a new shell tab
            newShell(palette)

            // Then the shell starts in the worktree of that session
            await(WindowTags.terminalTab(1))
            terminals.shellDirs shouldBe listOf(worktree)
        }

    @Test
    fun `FR-T7 new shell starts in home when chosen`() =
        runComposeUiTest {
            // Given the start directory set to home and the same selected session
            val palette = window(TerminalSettings(startDirectory = StartDirectory.HOME))
            selectSession()

            // When the user opens a new shell tab
            newShell(palette)

            // Then the shell starts in home, not in the worktree
            await(WindowTags.terminalTab(1))
            terminals.shellDirs shouldBe listOf(WINDOW_HOME)
        }

    @Test
    fun `FR-T7 shell tab is labelled with the chosen shell`() =
        runComposeUiTest {
            // Given the shell set to bash
            val palette = window(TerminalSettings(shell = ShellProfile.BASH))
            selectSession()

            // When the user opens a new shell tab
            newShell(palette)

            // Then the tab is labelled bash
            await(WindowTags.terminalTab(1)).assertTextContains("bash", substring = true)
        }
}
