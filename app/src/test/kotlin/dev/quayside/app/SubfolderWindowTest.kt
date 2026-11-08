package dev.quayside.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

private const val GROUP_WAIT_MS = 5_000L

/**
 * A group in the whole window on fakes (FR-P7, FR-S9, FR-K1; AM-306, docs/12 §4): the sidebar, Mission Control and
 * the palette on a project with subfolders. The order `ProjectsIntent.Select` then `SelectSession` of a popup Attach
 * and of "Nowy agent" is tested here because `WindowControllers` wires it (Lessons).
 */
@OptIn(ExperimentalTestApi::class)
class SubfolderWindowTest {
    @TempDir
    lateinit var root: Path

    private val terminals = RecordingTerminals()
    private val plantDir by lazy { Files.createDirectory(root.resolve("plant-puls")) }
    private val plant by lazy {
        aProject(
            id = "project0001",
            dir = plantDir,
            displayName = "plant-puls",
            subfolders = subfolderNames("docs", "soft"),
        )
    }

    private fun session(
        id: String,
        cwd: Path,
        index: Long,
    ) = WorldSession(SessionId(id), cwd, SessionState.Working, id, T0.plusSeconds(index))

    private val sessions by lazy {
        FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(plant.id, plant.dir)),
                sessions =
                    listOf(
                        session("rooted", plantDir, 0),
                        session("in-soft", plantDir.resolve("soft/api"), 1),
                        session("in-docs", plantDir.resolve("docs"), 2),
                    ),
            ),
        )
    }

    private fun attachCommand(id: SessionId) = ShellCommand(listOf("claude", "attach", id.value), root)

    private fun androidx.compose.ui.test.ComposeUiTest.gone(tag: String) =
        waitUntilDoesNotExist(hasTestTag(tag), GROUP_WAIT_MS)

    private fun androidx.compose.ui.test.ComposeUiTest.header(text: String) =
        waitUntilExactlyOneExists(hasText(text) and hasAnyAncestor(hasTestTag(WindowTags.HEADER)), GROUP_WAIT_MS)

    private val soft get() = WindowTags.subfolderRow("project0001", "soft")

    @Test
    fun `clicking a subfolder filters Mission Control`() =
        runComposeUiTest {
            // Given a group whose project page shows all three sessions
            showWindow(FakeProjectsApi(initial = listOf(plant)), sessions)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.missionRow("rooted"))
            await(WindowTags.missionRow("in-docs"))

            // When the user clicks the subfolder "soft"
            await(soft).performClick()

            // Then only its session is listed, the header names both, and the subfolder row is selected
            await(WindowTags.missionRow("in-soft"))
            gone(WindowTags.missionRow("rooted"))
            gone(WindowTags.missionRow("in-docs"))
            header("plant-puls / soft")
            await(soft).assertIsSelected()

            // And a click on the project row brings the whole project back
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.missionRow("rooted"))
            await(WindowTags.missionRow("in-docs"))
            header("plant-puls")
            await(soft).assertIsNotSelected()
        }

    @Test
    fun `the chevron hides the subfolder rows and selects the project`() =
        runComposeUiTest {
            showWindow(FakeProjectsApi(initial = listOf(plant)), sessions)
            await(soft).performClick()
            await(WindowTags.missionRow("in-soft"))
            gone(WindowTags.missionRow("rooted"))

            await(WindowTags.chevron("project0001")).performClick()

            gone(soft)
            await(WindowTags.missionRow("rooted"))
            await(WindowTags.missionRow("in-docs"))
            header("plant-puls")
        }

    @Test
    fun `palette go to subfolder selects it in sidebar and Mission Control`() =
        runComposeUiTest {
            // Given the palette over a window with no selection
            val palette = showWindow(FakeProjectsApi(initial = listOf(plant)), sessions)
            await(WindowTags.projectRow("project0001"))
            runOnIdle { palette.open() }

            // When the user finds "plant-puls / soft" and picks it
            await(WindowTags.PALETTE_FIELD).performTextInput("soft")
            await(WindowTags.paletteRow("projects.goto.project0001.sub1")).performClick()

            // Then the subfolder row is selected and Mission Control shows only its session
            waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE))
            await(soft).assertIsSelected()
            await(WindowTags.missionRow("in-soft"))
            gone(WindowTags.missionRow("rooted"))
            waitUntilExactlyOneExists(
                hasText("plant-puls / soft", substring = true) and hasAnyAncestor(hasTestTag(WindowTags.MODE_BAR)),
                GROUP_WAIT_MS,
            )
        }

    @Test
    fun `palette opens the subfolders dialog of the selected project`() =
        runComposeUiTest {
            val palette = showWindow(FakeProjectsApi(initial = listOf(plant)), sessions)
            await(WindowTags.projectRow("project0001")).performClick()
            runOnIdle { palette.open() }

            await(WindowTags.PALETTE_FIELD).performTextInput("podfoldery")
            await(WindowTags.paletteRow("projects.subfolders")).performClick()

            await(WindowTags.SUBFOLDERS_DIALOG)
        }

    @Test
    fun `popup attach from a subfolder page selects the session on the project page`() =
        runComposeUiTest {
            // Given the page of the subfolder "soft"
            val setup = WindowSetup(FakeProjectsApi(initial = listOf(plant)), sessions, terminals, ::attachCommand)
            showWindowOf(setup)
            await(soft).performClick()
            await(WindowTags.missionRow("in-soft"))
            gone(WindowTags.missionRow("rooted"))

            // When a popup Attach opens a session of the project directory, outside every subfolder
            runOnIdle {
                checkNotNull(setup.attacher).attachSession(
                    SessionId("rooted"),
                    sessions.snapshot.value.sessions,
                    listOf(plant),
                )
            }

            // Then the sidebar and Mission Control are on the project page and that session is selected
            await(WindowTags.terminalTab(1))
            header("plant-puls")
            await(WindowTags.projectRow("project0001")).assertIsSelected()
            await(soft).assertIsNotSelected()
            await(WindowTags.missionRow("rooted")).assertIsSelected()
            await(WindowTags.missionRow("in-soft"))
        }

    @Test
    fun `new agent from a subfolder page selects the session`() =
        runComposeUiTest {
            // Given the page of the subfolder "soft"
            showWindowOf(WindowSetup(FakeProjectsApi(initial = listOf(plant)), sessions))
            await(soft).performClick()
            await(WindowTags.missionRow("in-soft"))

            // When the user starts a new agent in the project
            await(WindowTags.NEW_AGENT_BUTTON).performClick()
            await(WindowTags.NEW_AGENT_DIALOG)
            onNodeWithTag(WindowTags.NEW_AGENT_PROMPT).performTextInput("Fix the login")
            onNodeWithTag(WindowTags.NEW_AGENT_START).performClick()
            awaitGone(WindowTags.NEW_AGENT_DIALOG)

            // Then the project page shows the new session selected (it runs in the project directory)
            header("plant-puls")
            await(WindowTags.missionRow("00000001")).assertIsSelected()
            await(WindowTags.projectRow("project0001")).assertIsSelected()
            await(soft).assertIsNotSelected()
        }
}
