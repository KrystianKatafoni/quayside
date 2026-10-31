package dev.agentmanager.projects.ui

import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.projects.api.A_PROJECT_DIR
import dev.agentmanager.projects.api.FakeProjectsApi
import dev.agentmanager.projects.api.aProject
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The public intents `Select` and `RenameSelectedRequested` that `app` sends from the palette (AM-408, decision 6). */
class ProjectsControllerIntentTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Agent Manager")
    private val other = aProject(id = "project0002", dir = A_PROJECT_DIR.resolveSibling("other"), displayName = "Other")

    private fun world(): ControllerWorld = ControllerWorld(projects = FakeProjectsApi(initial = listOf(project, other)))

    @Test
    fun `select intent selects a listed project`() =
        runTest {
            val controller = start(world())

            act { controller.onIntent(ProjectsIntent.Select(other.id)) }

            controller.state.value.selected shouldBe other.id
            controller.selection.value shouldBe other.id
        }

    @Test
    fun `select intent ignores an unknown project`() =
        runTest {
            val controller = start(world())
            act { controller.onIntent(ProjectsIntent.Select(project.id)) }

            act { controller.onIntent(ProjectsIntent.Select(ProjectId("gone"))) }

            controller.state.value.selected shouldBe project.id
        }

    @Test
    fun `select intent is ignored while a dialog is open`() =
        runTest {
            val controller = start(world())
            act { controller.onIntent(ProjectsIntent.Select(project.id)) }
            act { controller.onIntent(ProjectsIntent.RenameSelectedRequested) }

            act { controller.onIntent(ProjectsIntent.Select(other.id)) }

            controller.state.value.selected shouldBe project.id
            controller.state.value.dialog shouldBe Dialog.Rename(project, draft = "Agent Manager")
        }

    @Test
    fun `rename selected opens the dialog with the current name`() =
        runTest {
            val controller = start(world())
            act { controller.onIntent(ProjectsIntent.Select(other.id)) }

            act { controller.onIntent(ProjectsIntent.RenameSelectedRequested) }

            controller.state.value.dialog shouldBe Dialog.Rename(other, draft = "Other")
        }

    @Test
    fun `rename selected without a selection does nothing`() =
        runTest {
            val controller = start(world())

            act { controller.onIntent(ProjectsIntent.RenameSelectedRequested) }

            controller.state.value.dialog
                .shouldBeNull()
        }

    @Test
    fun `menu allows rename only with a selection and no dialog`() =
        runTest {
            val controller = start(world())
            controller.menu.value.canRename shouldBe false

            act { controller.onIntent(ProjectsIntent.Select(project.id)) }
            controller.menu.value.canRename shouldBe true

            act { controller.onIntent(ProjectsIntent.RenameSelectedRequested) }
            controller.menu.value.canRename shouldBe false

            act { controller.onEvent(SidebarEvent.Dismiss) }
            controller.menu.value.canRename shouldBe true
        }

    @Test
    fun `menu reports an open dialog`() =
        runTest {
            val controller = start(world())
            controller.menu.value.dialogOpen shouldBe false

            act { controller.onIntent(ProjectsIntent.Select(project.id)) }
            act { controller.onIntent(ProjectsIntent.RenameSelectedRequested) }
            controller.menu.value.dialogOpen shouldBe true

            act { controller.onEvent(SidebarEvent.Dismiss) }
            controller.menu.value.dialogOpen shouldBe false
        }
}
