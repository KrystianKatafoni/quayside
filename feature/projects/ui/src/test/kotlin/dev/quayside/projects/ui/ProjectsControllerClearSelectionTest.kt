package dev.quayside.projects.ui

import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * `ClearSelection`, sent by `app` while a global view of Mission Control is shown (AM-407, decision 1). Kept in its
 * own file so it goes away with the intent if the plan changes to variant A.
 */
class ProjectsControllerClearSelectionTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")
    private val other = aProject(id = "project0002", dir = A_PROJECT_DIR.resolveSibling("other"), displayName = "Other")

    private fun world(): ControllerWorld = ControllerWorld(projects = FakeProjectsApi(initial = listOf(project, other)))

    @Test
    fun `clear selection deselects the project`() =
        runTest {
            val controller = start(world())
            act { controller.onIntent(ProjectsIntent.Select(project.id)) }
            controller.menu.value.canRemove shouldBe true

            act { controller.onIntent(ProjectsIntent.ClearSelection) }

            controller.state.value.selected
                .shouldBeNull()
            controller.selection.value.shouldBeNull()
            // Without a selection ⌘⌫ and "Zmień nazwę" are off.
            controller.menu.value.canRemove shouldBe false
            controller.menu.value.canRename shouldBe false
            controller.menu.value.canAdd shouldBe true
        }

    @Test
    fun `clear selection is ignored while a dialog is open`() =
        runTest {
            val controller = start(world())
            act { controller.onIntent(ProjectsIntent.Select(project.id)) }
            act { controller.onIntent(ProjectsIntent.RenameSelectedRequested) }

            act { controller.onIntent(ProjectsIntent.ClearSelection) }

            controller.state.value.selected shouldBe project.id
            controller.state.value.dialog shouldBe Dialog.Rename(project, draft = "Quayside")
        }

    @Test
    fun `clear selection without a selection changes nothing`() =
        runTest {
            val controller = start(world())

            act { controller.onIntent(ProjectsIntent.ClearSelection) }

            controller.selection.value.shouldBeNull()
            controller.state.value.dialog
                .shouldBeNull()
        }

    @Test
    fun `the project that was cleared can be selected again`() =
        runTest {
            val controller = start(world())
            act { controller.onIntent(ProjectsIntent.Select(project.id)) }
            act { controller.onIntent(ProjectsIntent.ClearSelection) }

            act { controller.onIntent(ProjectsIntent.Select(project.id)) }

            controller.selection.value shouldBe project.id
            controller.menu.value.canRemove shouldBe true
        }
}
