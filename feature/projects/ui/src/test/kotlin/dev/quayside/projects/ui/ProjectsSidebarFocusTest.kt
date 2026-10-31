package dev.quayside.projects.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.StorageIssue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The sidebar buttons never hold the focus (issue #108): a focused button would turn the window shortcuts `⏎` (attach,
 * AM-505) and space (preview, AM-411) into a second click. Keys go to the root, so they reach whatever holds the focus.
 */
@OptIn(ExperimentalTestApi::class)
class ProjectsSidebarFocusTest {
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

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.openAndCancelAddDialog(world: ControllerWorld) {
        showSidebar(world)
        await(ProjectsTags.ADD_BUTTON).performClick()
        await(ProjectsTags.CONFIRM_ADD)
        await(ProjectsTags.CANCEL).performClick()
        waitUntilDoesNotExist(hasTestTag(ProjectsTags.CONFIRM_ADD))
    }

    @Test
    fun `FR-K2 add button does not take the focus when clicked`() =
        runComposeUiTest {
            // Given an empty sidebar whose add dialog was opened and cancelled
            val world = ControllerWorld()
            world.picker.willPick(A_PROJECT_DIR)
            openAndCancelAddDialog(world)

            // Then the button holds no focus
            onNodeWithTag(ProjectsTags.ADD_BUTTON).assertIsNotFocused()
        }

    @Test
    fun `FR-K2 Enter and space after clicking add do not open the add dialog again`() =
        runComposeUiTest {
            // Given the add dialog opened once and cancelled
            val world = ControllerWorld()
            world.picker.willPick(A_PROJECT_DIR)
            openAndCancelAddDialog(world)

            // When the user presses Enter and space
            press(Key.Enter)
            press(Key.Spacebar)

            // Then the folder chooser was not opened a second time
            world.picker.picks shouldBe 1
            onNodeWithTag(ProjectsTags.CONFIRM_ADD).assertDoesNotExist()
        }

    @Test
    fun `FR-K2 banner dismiss button cannot take the focus`() =
        runComposeUiTest {
            // Given a banner
            showSidebar(ControllerWorld(projects = FakeProjectsApi(storageIssue = StorageIssue.READ_FAILED)))
            await(ProjectsTags.BANNER_DISMISS)

            // When the user moves the focus with Tab (the way a click would request it)
            press(Key.Tab)

            // Then no button of the sidebar takes it, so Enter and space cannot click the button
            onNodeWithTag(ProjectsTags.BANNER_DISMISS).assertIsNotFocused()
            onAllNodes(isFocused()).assertCountEquals(0)
            press(Key.Enter)
            onNodeWithTag(ProjectsTags.BANNER).assertExists()
        }
}
