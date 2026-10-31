package dev.agentmanager.projects.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.testing.aSessionId
import dev.agentmanager.projects.api.A_PROJECT_DIR
import dev.agentmanager.projects.api.DirectoryReport
import dev.agentmanager.projects.api.FakeProjectsApi
import dev.agentmanager.projects.api.GitInfo
import dev.agentmanager.projects.api.HooksPresence
import dev.agentmanager.projects.api.InspectionWorld
import dev.agentmanager.projects.api.NameProblem
import dev.agentmanager.projects.api.ProjectError
import dev.agentmanager.projects.api.StorageIssue
import dev.agentmanager.projects.api.aProject
import dev.agentmanager.sessions.api.SessionState
import dev.agentmanager.sessions.api.WorldSession
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * User scenarios of the sidebar on fakes (FR-P1–FR-P4, docs/12 §4). They live here until
 * `tests:functional` exists (AM-904). The controller runs in the composition scope, like the window scope.
 */
@OptIn(ExperimentalTestApi::class)
class ProjectsSidebarTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR)
    private val working = WorldSession(aSessionId("working"), A_PROJECT_DIR)
    private val blocked =
        WorldSession(aSessionId("blocked"), A_PROJECT_DIR.resolve("src"), SessionState.AwaitingInput(null))

    private fun withSessions(vararg sessions: WorldSession = arrayOf(working)): ControllerWorld =
        ControllerWorld(projects = FakeProjectsApi(initial = listOf(project)), running = sessions.toList())

    private fun ComposeUiTest.showSidebar(world: ControllerWorld) {
        setContent {
            val scope = rememberCoroutineScope()
            val controller = remember { world.controller(scope) }
            ProjectsSidebar(controller)
        }
    }

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag))
        return onNodeWithTag(tag)
    }

    private fun ComposeUiTest.awaitGone(tag: String) = waitUntilDoesNotExist(hasTestTag(tag))

    /**
     * A text shown inside the node tagged [tag], e.g. a line of a dialog: on a descendant, or on the tagged
     * node itself when it merges its children. The first match, so a text repeated in the dialog is fine.
     */
    private fun ComposeUiTest.textIn(
        tag: String,
        text: String,
    ): SemanticsNodeInteraction =
        onAllNodes(hasText(text, substring = true) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag)))).onFirst()

    /** Opens the ⋯ menu of [project] and picks "Remove…". */
    private fun ComposeUiTest.askToRemove(id: ProjectId) {
        await(ProjectsTags.rowMenu(id)).performClick()
        await(ProjectsTags.ROW_MENU_REMOVE).performClick()
        await(ProjectsTags.CONFIRM_REMOVE)
    }

    /** Nothing inside the node tagged [tag] (or the node itself) shows [text]. */
    private fun ComposeUiTest.assertNoText(
        tag: String,
        text: String,
    ) = onAllNodes(hasText(text, substring = true) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))
        .assertCountEquals(0)

    /** Opens the ⋯ menu of the project and picks "Rename…". */
    private fun ComposeUiTest.askToRename(id: ProjectId) {
        await(ProjectsTags.rowMenu(id)).performClick()
        await(ProjectsTags.ROW_MENU_RENAME).performClick()
        await(ProjectsTags.RENAME_DIALOG)
    }

    /** Waits until [text] is shown inside the node tagged [tag] (the sidebar collects the snapshot asynchronously). */
    private fun ComposeUiTest.awaitText(
        tag: String,
        text: String,
    ) = waitUntil {
        onAllNodes(hasText(text, substring = true) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))
            .fetchSemanticsNodes()
            .isNotEmpty()
    }

    @Test
    fun `empty list shows the add button`() =
        runComposeUiTest {
            // Given no projects
            showSidebar(ControllerWorld())

            // Then
            await(ProjectsTags.EMPTY).assertIsDisplayed()
            textIn(ProjectsTags.SIDEBAR, ProjectsStrings["sidebar.empty"]).assertIsDisplayed()
            await(ProjectsTags.ADD_BUTTON).assertIsDisplayed().assertTextContains(ProjectsStrings["sidebar.add"])
        }

    @Test
    fun `FR-P1 adding a folder from disk shows it in the sidebar`() =
        runComposeUiTest {
            // Given
            val world = ControllerWorld(inspection = InspectionWorld(git = GitInfo("main")))
            world.picker.willPick(A_PROJECT_DIR)
            showSidebar(world)

            // When
            await(ProjectsTags.ADD_BUTTON).performClick()
            await(ProjectsTags.ADD).performClick()

            // Then
            await(ProjectsTags.row(project.id)).assertTextContains("agent-manager", substring = true).assertIsSelected()
            awaitGone(ProjectsTags.CONFIRM_ADD)
            onNodeWithTag(ProjectsTags.EMPTY).assertDoesNotExist()
            world.projects.calls shouldBe listOf("add $A_PROJECT_DIR")
        }

    @Test
    fun `FR-P2 add dialog shows branch, CLAUDE md, sessions and hooks`() =
        runComposeUiTest {
            // Given
            val inspection =
                InspectionWorld(
                    git = GitInfo("feature/login"),
                    claudeMd = true,
                    sessions = 2,
                    hooks = HooksPresence.PARTIAL,
                )
            val world = ControllerWorld(inspection = inspection, gatedInspection = true)
            world.picker.willPick(A_PROJECT_DIR)
            showSidebar(world)

            // When
            await(ProjectsTags.ADD_BUTTON).performClick()

            // Then "Checking…" until the report arrives, then every line of the report
            await(ProjectsTags.INSPECTING).assertTextContains(ProjectsStrings["add.inspecting"], substring = true)
            world.inspector.release()
            await(ProjectsTags.CONFIRM_ADD)
            val report = DirectoryReport(A_PROJECT_DIR, GitInfo("feature/login"), true, 2, HooksPresence.PARTIAL, null)
            textIn(ProjectsTags.CONFIRM_ADD, A_PROJECT_DIR.toString()).assertIsDisplayed()
            ReportSummary.lines(report).forEach { line ->
                textIn(ProjectsTags.CONFIRM_ADD, ProjectsStrings.format(line)).assertIsDisplayed()
            }
            world.projects.calls.shouldBeEmpty()
        }

    @Test
    fun `FR-P3 removing asks for confirmation and removes the row`() =
        runComposeUiTest {
            // Given a project without sessions
            val world = ControllerWorld(projects = FakeProjectsApi(initial = listOf(project)))
            showSidebar(world)

            // When
            askToRemove(project.id)

            // Then the row stays until the user confirms
            onNodeWithTag(ProjectsTags.row(project.id)).assertExists()
            world.projects.calls.shouldBeEmpty()
            await(ProjectsTags.REMOVE).assertTextContains(ProjectsStrings["remove.confirm"]).performClick()
            awaitGone(ProjectsTags.row(project.id))
            world.projects.calls shouldBe listOf("remove project0001")
            await(ProjectsTags.EMPTY).assertIsDisplayed()
        }

    @Test
    fun `FR-P4 stop sessions and remove`() =
        runComposeUiTest {
            // Given a project with a working session
            val world = withSessions()
            showSidebar(world)

            // When
            askToRemove(project.id)
            textIn(ProjectsTags.CONFIRM_REMOVE, ProjectsStrings.format(UiText("remove.activeSessions", listOf("1"))))
                .assertIsDisplayed()
            await(ProjectsTags.CANCEL).assertTextContains(ProjectsStrings["dialog.cancel"])
            await(
                ProjectsTags.STOP_AND_REMOVE,
            ).assertTextContains(ProjectsStrings["remove.stopAndRemove"]).performClick()

            // Then
            awaitGone(ProjectsTags.row(project.id))
            world.sessions.calls shouldBe listOf("stopAllIn project0001")
            world.projects.calls shouldBe listOf("remove project0001")
        }

    @Test
    fun `FR-P4 remove and keep sessions running`() =
        runComposeUiTest {
            // Given a project with a working session
            val world = withSessions()
            showSidebar(world)

            // When
            askToRemove(project.id)
            await(ProjectsTags.REMOVE_KEEP_SESSIONS)
                .assertTextContains(ProjectsStrings["remove.keepSessions"])
                .performClick()

            // Then
            awaitGone(ProjectsTags.row(project.id))
            world.sessions.calls.shouldBeEmpty()
            world.projects.calls shouldBe listOf("remove project0001")
        }

    @Test
    fun `FR-P4 failed stop keeps the project`() =
        runComposeUiTest {
            // Given a project with a working session that cannot be stopped
            val world = withSessions()
            world.sessions.failStop(working.id)
            showSidebar(world)

            // When
            askToRemove(project.id)
            await(ProjectsTags.STOP_AND_REMOVE).performClick()

            // Then a fixed message, the row stays and nothing is removed
            await(ProjectsTags.MESSAGE)
            textIn(ProjectsTags.MESSAGE, ProjectsStrings["remove.stopFailed"]).assertIsDisplayed()
            onNodeWithTag(ProjectsTags.row(project.id)).assertExists()
            world.projects.calls.shouldBeEmpty()
            world.sessions.calls shouldBe listOf("stopAllIn project0001")
        }

    @Test
    fun `FR-P6 renaming a project shows the new name in the sidebar`() =
        runComposeUiTest {
            // Given a project
            val world = ControllerWorld(projects = FakeProjectsApi(initial = listOf(project)))
            showSidebar(world)

            // When the user opens "Rename…", types a new name and saves
            askToRename(project.id)
            await(ProjectsTags.RENAME_FIELD).assertTextEquals("agent-manager").performTextReplacement("Moja aplikacja")
            await(ProjectsTags.RENAME_SAVE).assertTextContains(ProjectsStrings["rename.save"]).performClick()

            // Then
            awaitGone(ProjectsTags.RENAME_DIALOG)
            await(ProjectsTags.row(project.id)).assertTextContains("Moja aplikacja", substring = true)
            world.projects.calls shouldBe listOf("rename project0001")
        }

    @Test
    fun `FR-P6 invalid name shows a fixed message in the dialog`() =
        runComposeUiTest {
            // Given a project
            val world = ControllerWorld(projects = FakeProjectsApi(initial = listOf(project)))
            showSidebar(world)

            // When the user saves a blank name
            askToRename(project.id)
            await(ProjectsTags.RENAME_FIELD).performTextReplacement("   ")
            await(ProjectsTags.RENAME_SAVE).performClick()

            // Then the dialog stays with the typed text and a fixed message, and the name is unchanged
            val message = ProjectMessages.of(ProjectError.InvalidDisplayName(NameProblem.BLANK))
            awaitText(ProjectsTags.RENAME_DIALOG, ProjectsStrings.format(message))
            await(ProjectsTags.RENAME_FIELD).assertTextEquals("   ")
            await(ProjectsTags.row(project.id)).assertTextContains("agent-manager", substring = true)

            // And cancelling closes it without another write
            await(ProjectsTags.CANCEL).performClick()
            awaitGone(ProjectsTags.RENAME_DIALOG)
            world.projects.calls shouldBe listOf("rename project0001")
        }

    @Test
    fun `FR-P6 sidebar shows waiting and working counts per project`() =
        runComposeUiTest {
            // Given one working and one waiting session in the project and none in the other
            val other = aProject(id = "project0002", dir = A_PROJECT_DIR.resolveSibling("other"), displayName = "other")
            val world =
                ControllerWorld(
                    projects = FakeProjectsApi(initial = listOf(project, other)),
                    running = listOf(working, blocked),
                )

            // When
            showSidebar(world)

            // Then the project shows both counts, the other one none (zeros are not shown)
            awaitText(ProjectsTags.row(project.id), ProjectsStrings.format(UiText("row.waiting", listOf("1"))))
            textIn(ProjectsTags.row(project.id), ProjectsStrings.format(UiText("row.working", listOf("1"))))
                .assertIsDisplayed()
            assertNoText(ProjectsTags.row(other.id), "czeka")
            assertNoText(ProjectsTags.row(other.id), "pracuje")
        }

    @Test
    @Suppress("ForbiddenMethodCall") // runBlocking is allowed in tests (ENG-C3); setState suspends.
    fun `FR-P6 counts change when a session starts waiting`() =
        runComposeUiTest {
            // Given a project with one working session
            val world = withSessions()
            showSidebar(world)
            awaitText(ProjectsTags.row(project.id), ProjectsStrings.format(UiText("row.working", listOf("1"))))

            // When the session starts waiting for the user
            runBlocking { world.sessions.setState(working.id, SessionState.AwaitingPermission("Bash")) }

            // Then the row shows "waiting: 1" and no "working" count any more
            awaitText(ProjectsTags.row(project.id), ProjectsStrings.format(UiText("row.waiting", listOf("1"))))
            assertNoText(ProjectsTags.row(project.id), "pracuje")
        }

    @Test
    fun `storage issue shows a fixed banner`() =
        runComposeUiTest {
            // Given a list file that could not be read
            showSidebar(ControllerWorld(projects = FakeProjectsApi(storageIssue = StorageIssue.READ_FAILED)))

            // Then
            await(ProjectsTags.BANNER)
            textIn(ProjectsTags.BANNER, ProjectsStrings.format(ProjectMessages.of(StorageIssue.READ_FAILED)))
                .assertIsDisplayed()

            // When dismissed, it is gone
            await(ProjectsTags.BANNER_DISMISS).performClick()
            awaitGone(ProjectsTags.BANNER)
        }
}
