package dev.quayside.projects.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The window-menu and palette entry to the rename dialog, as `app` calls it (FR-P6, AM-408). */
@OptIn(ExperimentalTestApi::class)
class ProjectsRenameFromMenuTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR)

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag))
        return onNodeWithTag(tag)
    }

    @Test
    fun `FR-P6 rename from the window menu intent shows the new name in the sidebar`() =
        runComposeUiTest {
            // Given a project, selected in the sidebar
            val world = ControllerWorld(projects = FakeProjectsApi(initial = listOf(project)))
            var controller: ProjectsController? = null
            setContent {
                val scope = rememberCoroutineScope()
                val created = remember { world.controller(scope) }
                controller = created
                ProjectsSidebar(created)
            }
            await(ProjectsTags.row(project.id)).performClick()

            // When the menu item "Rename project…" sends its intent
            runOnIdle { controller?.onIntent(ProjectsIntent.RenameSelectedRequested) }

            // Then the rename dialog opens with the current name, and saving shows the new name
            await(ProjectsTags.RENAME_FIELD).assertTextEquals("agent-manager").performTextReplacement("Moja aplikacja")
            await(ProjectsTags.RENAME_SAVE).performClick()
            waitUntilDoesNotExist(hasTestTag(ProjectsTags.RENAME_DIALOG))
            await(ProjectsTags.row(project.id)).assertTextContains("Moja aplikacja", substring = true)
            world.projects.calls shouldBe listOf("rename project0001")
        }
}
