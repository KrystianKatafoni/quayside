package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.quayside.commandpalette.ui.CommandPaletteController
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import dev.quayside.terminal.api.FakeTerminalSettingsRepo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * The terminal settings dialog in the whole window on fakes (FR-T7, AM-508). The project `Quayside` has two
 * sessions; shells and the settings they were opened with are recorded by [RecordingTerminals].
 */
@OptIn(ExperimentalTestApi::class)
class TerminalSettingsDialogWindowTest {
    @TempDir
    lateinit var root: Path

    private val repo = FakeTerminalSettingsRepo()
    private val terminals = RecordingTerminals(settings = repo.settings)
    private val projectDir by lazy { Files.createDirectory(root.resolve("quayside")) }
    private val project by lazy { aProject(id = "project0001", dir = projectDir, displayName = "Quayside") }

    private fun session(
        name: String,
        startedAtSecond: Long,
    ): WorldSession =
        WorldSession(
            id = SessionId(name),
            cwd = projectDir,
            state = SessionState.Working,
            name = name,
            startedAt = T0.plusSeconds(startedAtSecond),
        )

    private fun sessions(): FakeSessions =
        FakeSessions(
            SessionsWorld(
                listOf(WorldProject(project.id, project.dir)),
                listOf(session("older-work", 0), session("newer-work", 60)),
            ),
        )

    private fun ComposeUiTest.window(): CommandPaletteController =
        showWindow(FakeProjectsApi(initial = listOf(project)), sessions(), terminals, terminalSettings = repo)

    private fun ComposeUiTest.selectProject() {
        await(WindowTags.projectRow("project0001")).performClick()
        await(WindowTags.DETAILS_EMPTY)
    }

    /** Picks [commandId] (titled [titleKey]) in the palette. */
    private fun ComposeUiTest.runPaletteCommand(
        palette: CommandPaletteController,
        commandId: String,
        titleKey: String,
    ) {
        runOnIdle { palette.open() }
        await(WindowTags.PALETTE_FIELD).performTextInput(Strings[titleKey])
        await(WindowTags.paletteRow(commandId)).performClick()
        waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE))
    }

    private fun ComposeUiTest.openSettings(palette: CommandPaletteController) =
        runPaletteCommand(palette, "terminal.settings", "command.terminalSettings")

    private fun ComposeUiTest.newShell(palette: CommandPaletteController) =
        runPaletteCommand(palette, "terminal.newShell", "command.newShell")

    private fun ComposeUiTest.press(keys: List<Key>) =
        keys.forEach { key ->
            onNodeWithTag(WindowTags.KEYS).performKeyInput {
                keyDown(key)
                keyUp(key)
            }
            waitForIdle()
        }

    @Test
    fun `FR-T7 palette opens terminal settings`() =
        runComposeUiTest {
            // Given the window with the default settings and the closed palette
            val palette = window()
            onNodeWithTag(WindowTags.SETTINGS_DIALOG).assertDoesNotExist()

            // When the user picks "Ustawienia terminala" in the palette
            openSettings(palette)

            // Then the dialog shows the saved settings and the palette is gone
            await(WindowTags.SETTINGS_DIALOG)
            await(WindowTags.SETTINGS_FONT_SIZE).assertTextContains("13")
            onNodeWithTag(WindowTags.PALETTE).assertDoesNotExist()
        }

    @Test
    fun `FR-T7 saved font size applies to the next tab`() =
        runComposeUiTest {
            // Given a shell tab opened with the default font size
            val palette = window()
            selectProject()
            newShell(palette)
            await(WindowTags.terminalTab(1))
            terminals.settingsAtOpen.map { it.fontSize } shouldBe listOf(13)

            // When the user saves font size 20 in the dialog
            openSettings(palette)
            await(WindowTags.SETTINGS_FONT_SIZE).performTextReplacement("20")
            await(WindowTags.SETTINGS_SAVE).performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.SETTINGS_DIALOG))

            // Then the open tab is untouched and the next tab is opened with 20
            repo.settings.value.fontSize shouldBe 20
            newShell(palette)
            await(WindowTags.terminalTab(2))
            terminals.settingsAtOpen.map { it.fontSize } shouldBe listOf(13, 20)
        }

    /**
     * Keys go to the dialog because its popup takes the focus (a focusable `Popup`), not because the commands
     * check for it; the guard of the commands is tested in [TerminalSettingsCommandsTest].
     */
    @Test
    fun `FR-T7 window shortcuts do nothing while the dialog is open`() =
        runComposeUiTest {
            // Given a selected project with sessions and the settings dialog open
            val palette = window()
            selectProject()
            openSettings(palette)
            await(WindowTags.SETTINGS_DIALOG)

            // When the user presses j and then g p
            press(listOf(Key.J, Key.G, Key.P))

            // Then no session is selected, and no palette opened
            onNodeWithTag(WindowTags.DETAILS_EMPTY).assertExists()
            onNodeWithTag(WindowTags.DETAILS).assertDoesNotExist()
            onNodeWithTag(WindowTags.PALETTE).assertDoesNotExist()
            onNodeWithTag(WindowTags.SETTINGS_DIALOG).assertExists()
        }
}
