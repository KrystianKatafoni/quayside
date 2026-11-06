package dev.quayside.projects.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.testing.aSessionId
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The hooks warning in the remove dialog as the user sees it (FR-P3, FR-P4, NFR-5, AM-610), on fakes. The start focus
 * is "Anuluj": Space and Enter on a focused button count as a click, so no other button may hold it.
 */
@OptIn(ExperimentalTestApi::class)
class ConfirmRemoveHooksDialogTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR)
    private val working = WorldSession(aSessionId("working"), A_PROJECT_DIR)

    private fun world(
        state: ProjectHooksState,
        running: List<WorldSession> = emptyList(),
    ): ControllerWorld =
        ControllerWorld(projects = FakeProjectsApi(initial = listOf(project)), running = running).also {
            it.hooks.has(A_PROJECT_DIR, state)
        }

    private fun ComposeUiTest.showSidebar(world: ControllerWorld) {
        setContent {
            AmTestTheme {
                val scope = rememberCoroutineScope()
                val controller = remember { world.controller(scope) }
                ProjectsSidebar(controller)
            }
        }
    }

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag))
        return onNodeWithTag(tag)
    }

    private fun ComposeUiTest.awaitFocus(tag: String) = waitUntilExactlyOneExists(hasTestTag(tag) and isFocused())

    private fun ComposeUiTest.textIn(
        tag: String,
        text: String,
    ): SemanticsNodeInteraction =
        onAllNodes(hasText(text, substring = true) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag)))).onFirst()

    private fun ComposeUiTest.hasTextIn(
        tag: String,
        text: String,
    ): Boolean =
        onAllNodes(hasText(text, substring = true) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))
            .fetchSemanticsNodes()
            .isNotEmpty()

    private fun ComposeUiTest.askToRemove(id: ProjectId) {
        await(ProjectsTags.rowMenu(id)).performClick()
        await(ProjectsTags.ROW_MENU_REMOVE).performClick()
        await(ProjectsTags.CONFIRM_REMOVE)
    }

    private fun ComposeUiTest.press(key: Key) {
        // The popup is a second root; Space on "Anuluj" closes it, so the last root may be the window.
        onAllNodes(isRoot()).onLast().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        waitForIdle()
    }

    @Test
    fun `FR-P3 dialog warns about hooks left in settings local json`() =
        runComposeUiTest {
            // Given a project with our hooks in its local settings
            val world = world(ProjectHooksState.PRESENT)
            showSidebar(world)

            // When the user asks to remove it
            askToRemove(project.id)

            // Then the dialog says the hooks stay in the file and offers to uninstall them; nothing changed yet
            textIn(ProjectsTags.CONFIRM_REMOVE, ProjectsStrings["remove.hooks.present"]).assertIsDisplayed()
            await(ProjectsTags.UNINSTALL_HOOKS).assertTextContains(ProjectsStrings["remove.hooks.uninstall"])
            await(ProjectsTags.REMOVE).assertIsDisplayed()
            world.hooks.calls.none { it.startsWith("uninstall") } shouldBe true
            world.projects.calls.shouldBeEmpty()
        }

    @Test
    fun `FR-P3 no warning without hooks`() =
        runComposeUiTest {
            // Given a project without our hooks
            val world = world(ProjectHooksState.ABSENT)
            showSidebar(world)

            // When
            askToRemove(project.id)

            // Then the dialog is the old one
            await(ProjectsTags.REMOVE)
            onNodeWithTag(ProjectsTags.UNINSTALL_HOOKS).assertDoesNotExist()
            hasTextIn(ProjectsTags.CONFIRM_REMOVE, ProjectsStrings["remove.hooks.present"]) shouldBe false
            hasTextIn(ProjectsTags.CONFIRM_REMOVE, ProjectsStrings["remove.hooks.unknown"]) shouldBe false
        }

    @Test
    fun `FR-P3 unreadable settings say so and offer no uninstall`() =
        runComposeUiTest {
            // Given a project whose local settings cannot be read
            val world = world(ProjectHooksState.UNKNOWN)
            showSidebar(world)

            // When
            askToRemove(project.id)

            // Then the dialog says so, the project can still be removed, and there is no uninstall button
            textIn(ProjectsTags.CONFIRM_REMOVE, ProjectsStrings["remove.hooks.unknown"]).assertIsDisplayed()
            onNodeWithTag(ProjectsTags.UNINSTALL_HOOKS).assertDoesNotExist()
            await(ProjectsTags.REMOVE).assertIsDisplayed()
        }

    @Test
    fun `NFR-5 cancel has the start focus`() =
        runComposeUiTest {
            // Given a project with hooks
            showSidebar(world(ProjectHooksState.PRESENT))

            // When the dialog opens
            askToRemove(project.id)

            // Then the focus is on "Anuluj"
            awaitFocus(ProjectsTags.CANCEL)
        }

    @Test
    fun `NFR-5 cancel has the start focus without hooks`() =
        runComposeUiTest {
            showSidebar(world(ProjectHooksState.ABSENT))

            askToRemove(project.id)

            awaitFocus(ProjectsTags.CANCEL)
        }

    @Test
    fun `NFR-5 cancel has the start focus after uninstall`() =
        runComposeUiTest {
            // Given the dialog with hooks
            showSidebar(world(ProjectHooksState.PRESENT))
            askToRemove(project.id)

            // When the user uninstalls them
            await(ProjectsTags.UNINSTALL_HOOKS).performClick()
            waitUntilDoesNotExist(hasTestTag(ProjectsTags.UNINSTALL_HOOKS))

            // Then the focus is back on "Anuluj", not on a button that changes files
            awaitFocus(ProjectsTags.CANCEL)
        }

    @Test
    fun `NFR-5 Space at start closes the dialog and changes nothing`() =
        runComposeUiTest {
            // Given the dialog with hooks and active sessions, just opened
            val world = world(ProjectHooksState.PRESENT, listOf(working))
            showSidebar(world)
            askToRemove(project.id)
            awaitFocus(ProjectsTags.CANCEL)

            // When the user presses Space
            press(Key.Spacebar)

            // Then the key reached "Anuluj" (the dialog is gone) and nothing else was touched
            onNodeWithTag(ProjectsTags.CONFIRM_REMOVE).assertDoesNotExist()
            assertUntouched(world)
        }

    @Test
    fun `NFR-5 Enter at start closes the dialog and changes nothing`() =
        runComposeUiTest {
            // Given the dialog with hooks and active sessions, just opened
            val world = world(ProjectHooksState.PRESENT, listOf(working))
            showSidebar(world)
            askToRemove(project.id)
            awaitFocus(ProjectsTags.CANCEL)

            // When the user presses Enter
            press(Key.Enter)

            // Then the key reached "Anuluj" (the dialog is gone) and nothing else was touched
            onNodeWithTag(ProjectsTags.CONFIRM_REMOVE).assertDoesNotExist()
            assertUntouched(world)
        }

    private fun ComposeUiTest.assertUntouched(world: ControllerWorld) {
        world.hooks.calls.none { it.startsWith("uninstall") } shouldBe true
        world.projects.calls.shouldBeEmpty()
        world.sessions.calls.shouldBeEmpty()
        onNodeWithTag(ProjectsTags.row(project.id)).assertExists()
    }

    @Test
    fun `NFR-5 uninstall button removes hooks and shows the notice`() =
        runComposeUiTest {
            // Given the dialog with hooks
            val world = world(ProjectHooksState.PRESENT)
            showSidebar(world)
            askToRemove(project.id)

            // When the user clicks "Odinstaluj hooki"
            await(ProjectsTags.UNINSTALL_HOOKS).performClick()

            // Then only the hooks go: the dialog stays with a notice, the warning and the button are gone
            waitUntilDoesNotExist(hasTestTag(ProjectsTags.UNINSTALL_HOOKS))
            textIn(ProjectsTags.CONFIRM_REMOVE, ProjectsStrings["remove.hooks.uninstalled"]).assertIsDisplayed()
            hasTextIn(ProjectsTags.CONFIRM_REMOVE, ProjectsStrings["remove.hooks.present"]) shouldBe false
            world.hooks.calls.filter { it.startsWith("uninstall") } shouldBe listOf("uninstall $A_PROJECT_DIR")
            world.projects.calls.shouldBeEmpty()
            onNodeWithTag(ProjectsTags.row(project.id)).assertExists()
        }

    @Test
    fun `FR-P4 session choices stay with the hooks warning`() =
        runComposeUiTest {
            // Given a project with hooks and a working session
            val world = world(ProjectHooksState.PRESENT, listOf(working))
            showSidebar(world)

            // When
            askToRemove(project.id)

            // Then all three choices and the uninstall button are there; stopping and removing leaves the hooks alone
            await(ProjectsTags.UNINSTALL_HOOKS)
            await(ProjectsTags.REMOVE_KEEP_SESSIONS)
            await(ProjectsTags.CANCEL)
            await(ProjectsTags.STOP_AND_REMOVE).performClick()
            waitUntilDoesNotExist(hasTestTag(ProjectsTags.row(project.id)))
            world.sessions.calls shouldBe listOf("stopAllIn project0001")
            world.projects.calls shouldBe listOf("remove project0001")
            world.hooks.calls.none { it.startsWith("uninstall") } shouldBe true
        }
}
