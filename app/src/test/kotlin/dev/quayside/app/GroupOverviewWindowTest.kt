package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
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

private const val OVERVIEW_WAIT_MS = 5_000L

/**
 * The group overview in the whole window on fakes (FR-P8, FR-P7, AM-307, docs/12 §4): a click on a subfolder row
 * goes through `QuaysideApp.handlersOf` to the sidebar, and Mission Control follows its focus. Sessions are named
 * `rooted`, `soft-new`, `soft-old`, `in-docs` after their directory; the subfolders are `docs` and `soft`.
 */
@OptIn(ExperimentalTestApi::class)
class GroupOverviewWindowTest {
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
                        session("soft-old", plantDir.resolve("soft/api"), 1),
                        session("in-docs", plantDir.resolve("docs"), 2),
                        session("soft-new", plantDir.resolve("soft"), 3),
                    ),
            ),
        )
    }

    private fun attachCommand(id: SessionId) = ShellCommand(listOf("claude", "attach", id.value), root)

    private val soft get() = WindowTags.subfolderRow("project0001", "soft")

    private fun ComposeUiTest.gone(tag: String) = waitUntilDoesNotExist(hasTestTag(tag), OVERVIEW_WAIT_MS)

    private fun ComposeUiTest.header(text: String) =
        waitUntilExactlyOneExists(hasText(text) and hasAnyAncestor(hasTestTag(WindowTags.HEADER)), OVERVIEW_WAIT_MS)

    private fun ComposeUiTest.press(key: Key) {
        onNodeWithTag(WindowTags.KEYS).performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.detailsShow(name: String) =
        waitUntil(timeoutMillis = OVERVIEW_WAIT_MS) {
            onAllNodes(hasText(name, substring = true) and hasAnyAncestor(hasTestTag(WindowTags.DETAILS)))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

    @Test
    fun `clicking a subfolder in the overview selects it in the sidebar and Mission Control`() =
        runComposeUiTest {
            // Given the page of the group, with the overview and the counter of "soft"
            showWindow(FakeProjectsApi(initial = listOf(plant)), sessions)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.OVERVIEW)
            await(WindowTags.missionRow("rooted"))
            waitUntil(timeoutMillis = OVERVIEW_WAIT_MS) {
                onAllNodes(
                    hasContentDescription("2 pracują") and hasAnyAncestor(hasTestTag(WindowTags.overviewRow("soft"))),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().isNotEmpty()
            }

            // When the user clicks the row "soft" in the overview
            await(WindowTags.overviewRow("soft")).performClick()

            // Then the sidebar and Mission Control show the subfolder: the header names both and
            // only its sessions are listed
            await(soft).assertIsSelected()
            await(WindowTags.projectRow("project0001")).assertIsNotSelected()
            header("plant-puls / soft")
            await(WindowTags.missionRow("soft-new"))
            await(WindowTags.missionRow("soft-old"))
            gone(WindowTags.missionRow("rooted"))
            gone(WindowTags.missionRow("in-docs"))
            // and the overview gives way to the page of the subfolder
            gone(WindowTags.OVERVIEW)
        }

    @Test
    fun `overview click on a collapsed group expands it and selects the subfolder row`() =
        runComposeUiTest {
            // Given a collapsed group (the chevron hides the subfolder rows and keeps the project page)
            showWindow(FakeProjectsApi(initial = listOf(plant)), sessions)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.chevron("project0001")).performClick()
            gone(soft)
            await(WindowTags.overviewRow("soft"))

            // When the user clicks "soft" in the overview
            await(WindowTags.overviewRow("soft")).performClick()

            // Then the group is open again with the subfolder row selected
            await(soft).assertIsSelected()
            header("plant-puls / soft")
            await(WindowTags.missionRow("soft-new"))
        }

    @Test
    fun `after an overview click j moves over the subfolder sessions`() =
        runComposeUiTest {
            // Given the page of the subfolder "soft" opened from the overview
            showWindow(FakeProjectsApi(initial = listOf(plant)), sessions)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.overviewRow("soft")).performClick()
            await(WindowTags.missionRow("soft-new"))
            waitForIdle()

            // When the user presses j three times
            press(Key.J)
            detailsShow("soft-new")
            press(Key.J)
            detailsShow("soft-old")
            press(Key.J)

            // Then the selection stays on the last session of the subfolder (it does not leave for "rooted")
            detailsShow("soft-old")
        }

    @Test
    fun `Space after an overview click opens the preview`() =
        runComposeUiTest {
            showWindow(FakeProjectsApi(initial = listOf(plant)), sessions)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.overviewRow("soft")).performClick()
            await(WindowTags.missionRow("soft-new"))
            waitForIdle()
            press(Key.J)
            detailsShow("soft-new")

            // When the user presses Space
            press(Key.Spacebar)

            // Then the window gets it (nothing in the overview took the focus with the click)
            await(WindowTags.PREVIEW)
        }

    @Test
    fun `Enter after an overview click attaches the selected session`() =
        runComposeUiTest {
            showWindow(FakeProjectsApi(initial = listOf(plant)), sessions, terminals, ::attachCommand)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.overviewRow("soft")).performClick()
            await(WindowTags.missionRow("soft-new"))
            waitForIdle()
            press(Key.J)
            detailsShow("soft-new")

            press(Key.Enter)

            await(WindowTags.terminalTab(1))
        }

    @Test
    fun `clicking the group row brings the overview back`() =
        runComposeUiTest {
            // Given the page of a subfolder, reached from the overview
            showWindow(FakeProjectsApi(initial = listOf(plant)), sessions)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.overviewRow("soft")).performClick()
            gone(WindowTags.OVERVIEW)

            // When the user clicks the group row
            await(WindowTags.projectRow("project0001")).performClick()

            // Then the overview and the whole project are back
            await(WindowTags.overviewRow("docs"))
            await(WindowTags.missionRow("rooted"))
            header("plant-puls")
            await(soft).assertIsNotSelected()
        }
}
