package dev.quayside.projects.ui

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.testing.aSessionId
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.SessionCounts
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Path

/**
 * The hooks of a project in the remove dialog (FR-P3, FR-P4, NFR-5, AM-610): read before the dialog opens, removed
 * only by "Odinstaluj hooki", never together with the project. The guards of the controller are tested here
 * because the UI also disables the buttons (the controller must not rely on that).
 */
class ProjectsRemoveHooksControllerTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR)
    private val other = aProject(id = "project0002", dir = Path.of("/Users/test/projects/other-project"))
    private val ended = WorldSession(aSessionId("done"), A_PROJECT_DIR, SessionState.Done)
    private val working = WorldSession(aSessionId("working"), A_PROJECT_DIR)
    private val none = SessionCounts(0, 0)

    private fun world(running: List<WorldSession> = listOf(ended)): ControllerWorld =
        ControllerWorld(projects = FakeProjectsApi(initial = listOf(project, other)), running = running)

    /** Selects [target] and asks to remove it, as ⌘⌫ does. */
    private fun TestScope.requestRemove(
        controller: ProjectsController,
        target: dev.quayside.projects.api.Project = project,
    ) {
        act { controller.onEvent(SidebarEvent.Select(target.id)) }
        act { controller.onIntent(ProjectsIntent.RemoveSelectedRequested) }
    }

    private fun confirm(
        hooks: ProjectHooksState,
        notice: UiText? = null,
        active: SessionCounts? = none,
    ) = Dialog.ConfirmRemove(project, active, hooks, notice)

    private fun ControllerWorld.uninstalls(): List<String> = hooks.calls.filter { it.startsWith("uninstall") }

    @Test
    fun `remove request checks the hooks before the confirmation`() =
        runTest {
            val world = world()
            val gate = world.hooks.holdStatus(A_PROJECT_DIR)
            val controller = start(world)

            requestRemove(controller)

            controller.state.value.dialog shouldBe Dialog.Waiting.CHECKING_HOOKS
            world.hooks.calls shouldBe listOf("status $A_PROJECT_DIR")
            gate.complete(Unit)
            act { }
            controller.state.value.dialog shouldBe confirm(ProjectHooksState.ABSENT)
        }

    @Test
    fun `confirmation shows present hooks`() =
        runTest {
            val world = world()
            world.hooks.has(A_PROJECT_DIR, ProjectHooksState.PRESENT)
            val controller = start(world)

            requestRemove(controller)

            controller.state.value.dialog shouldBe confirm(ProjectHooksState.PRESENT)
            world.projects.calls.shouldBeEmpty()
        }

    @Test
    fun `confirmation shows unknown hooks`() =
        runTest {
            val world = world()
            world.hooks.has(A_PROJECT_DIR, ProjectHooksState.UNKNOWN)
            val controller = start(world)

            requestRemove(controller)

            controller.state.value.dialog shouldBe confirm(ProjectHooksState.UNKNOWN)
        }

    @Test
    fun `absent hooks show the dialog as before`() =
        runTest {
            val world = world()
            val controller = start(world)

            requestRemove(controller)
            controller.state.value.dialog shouldBe Dialog.ConfirmRemove(project, active = none)

            act { controller.onEvent(SidebarEvent.RemoveKeepSessions) }
            world.projects.calls shouldBe listOf("remove project0001")
            world.uninstalls().shouldBeEmpty()
        }

    @Test
    fun `dismiss while checking cancels and closes`() =
        runTest {
            val world = world()
            world.hooks.holdStatus(A_PROJECT_DIR)
            val controller = start(world)
            requestRemove(controller)

            act { controller.onEvent(SidebarEvent.Dismiss) }

            controller.state.value.dialog shouldBe null
            controller.state.value.projects shouldBe listOf(project, other)
            world.projects.calls.shouldBeEmpty()
            world.uninstalls().shouldBeEmpty()
        }

    @Test
    fun `hook status after dismiss does not reopen the dialog`() =
        runTest {
            // Given the read of the first project is still running after the user cancelled it
            val world = world()
            world.hooks.has(A_PROJECT_DIR, ProjectHooksState.PRESENT)
            val late = world.hooks.holdStatus(A_PROJECT_DIR)
            val controller = start(world)
            requestRemove(controller)
            act { controller.onEvent(SidebarEvent.Dismiss) }

            // When the user opens the dialog of another project and the old read finishes meanwhile
            requestRemove(controller, other)
            val second = controller.state.value.dialog
            late.complete(Unit)
            act { }

            // Then the dialog of the other project stays, untouched by the old result
            second shouldBe Dialog.ConfirmRemove(other, none, ProjectHooksState.ABSENT)
            controller.state.value.dialog shouldBe second
        }

    @Test
    fun `hook status after dismiss does not open a dialog by itself`() =
        runTest {
            val world = world()
            world.hooks.has(A_PROJECT_DIR, ProjectHooksState.PRESENT)
            val late = world.hooks.holdStatus(A_PROJECT_DIR)
            val controller = start(world)
            requestRemove(controller)
            act { controller.onEvent(SidebarEvent.Dismiss) }

            late.complete(Unit)
            act { }

            controller.state.value.dialog shouldBe null
        }

    @Test
    fun `uninstall removes the hooks and keeps the dialog with the new status`() =
        runTest {
            val world = world()
            world.hooks.has(A_PROJECT_DIR, ProjectHooksState.PRESENT)
            val gate = world.hooks.holdUninstall(A_PROJECT_DIR)
            val controller = start(world)
            requestRemove(controller)

            act { controller.onEvent(SidebarEvent.UninstallHooks) }
            controller.state.value.dialog shouldBe Dialog.Waiting.BUSY
            gate.complete(Unit)
            act { }

            world.uninstalls() shouldBe listOf("uninstall $A_PROJECT_DIR")
            world.hooks.calls shouldBe
                listOf("status $A_PROJECT_DIR", "uninstall $A_PROJECT_DIR", "status $A_PROJECT_DIR")
            controller.state.value.dialog shouldBe
                confirm(ProjectHooksState.ABSENT, notice = UiText("remove.hooks.uninstalled"))
        }

    @Test
    fun `uninstall keeps the frozen session counts`() =
        runTest {
            val world = world(listOf(working))
            world.hooks.has(A_PROJECT_DIR, ProjectHooksState.PRESENT)
            val controller = start(world)
            requestRemove(controller)
            val frozen = SessionCounts(working = 1, waiting = 0)
            (controller.state.value.dialog as Dialog.ConfirmRemove).active shouldBe frozen

            world.sessions.appear(WorldSession(aSessionId("second"), A_PROJECT_DIR))
            act { }
            // The new snapshot reached the state, so a fresh read would show 2 sessions
            controller.state.value.sessions shouldBe freshView(project.id to SessionCounts(2, 0))
            act { controller.onEvent(SidebarEvent.UninstallHooks) }

            (controller.state.value.dialog as Dialog.ConfirmRemove).active shouldBe frozen
        }

    @Test
    fun `uninstall does not remove the project`() =
        runTest {
            val world = world(listOf(working))
            world.hooks.has(A_PROJECT_DIR, ProjectHooksState.PRESENT)
            val controller = start(world)
            requestRemove(controller)

            act { controller.onEvent(SidebarEvent.UninstallHooks) }

            world.projects.calls.shouldBeEmpty()
            world.sessions.calls.shouldBeEmpty()
            controller.state.value.projects shouldBe listOf(project, other)
            controller.state.value.selected shouldBe project.id
        }

    @ParameterizedTest
    @EnumSource(ProjectHooksError::class)
    fun `uninstall failure shows the category and reads the status again`(error: ProjectHooksError) =
        runTest {
            // Given the file changed behind the dialog while the removal failed
            val world = world()
            world.hooks.has(A_PROJECT_DIR, ProjectHooksState.PRESENT)
            world.hooks.failNextUninstall(error, then = ProjectHooksState.UNKNOWN)
            val controller = start(world)
            requestRemove(controller)

            act { controller.onEvent(SidebarEvent.UninstallHooks) }

            val key =
                when (error) {
                    ProjectHooksError.UNREADABLE -> "remove.hooks.failed.unreadable"
                    ProjectHooksError.WRITE_FAILED -> "remove.hooks.failed.writeFailed"
                }
            world.hooks.calls.last() shouldBe "status $A_PROJECT_DIR"
            controller.state.value.dialog shouldBe confirm(ProjectHooksState.UNKNOWN, notice = UiText(key))
            world.projects.calls.shouldBeEmpty()
        }

    @Test
    fun `uninstall is ignored when hooks are absent`() =
        runTest {
            val world = world()
            val controller = start(world)
            requestRemove(controller)

            act { controller.onEvent(SidebarEvent.UninstallHooks) }

            world.uninstalls().shouldBeEmpty()
            controller.state.value.dialog shouldBe confirm(ProjectHooksState.ABSENT)
        }

    @Test
    fun `uninstall is ignored when hooks are unknown`() =
        runTest {
            val world = world()
            world.hooks.has(A_PROJECT_DIR, ProjectHooksState.UNKNOWN)
            val controller = start(world)
            requestRemove(controller)

            act { controller.onEvent(SidebarEvent.UninstallHooks) }

            world.uninstalls().shouldBeEmpty()
            controller.state.value.dialog shouldBe confirm(ProjectHooksState.UNKNOWN)
        }

    @Test
    fun `uninstall is ignored outside the remove dialog`() =
        runTest {
            val world = world()
            world.hooks.has(A_PROJECT_DIR, ProjectHooksState.PRESENT)
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.Select(project.id)) }

            // No dialog
            act { controller.onEvent(SidebarEvent.UninstallHooks) }
            controller.state.value.dialog shouldBe null

            // The rename dialog of a project that has hooks
            act { controller.onIntent(ProjectsIntent.RenameSelectedRequested) }
            val rename = controller.state.value.dialog
            act { controller.onEvent(SidebarEvent.UninstallHooks) }
            controller.state.value.dialog shouldBe rename

            world.uninstalls().shouldBeEmpty()
        }

    @Test
    fun `uninstall is ignored while the hooks are being read`() =
        runTest {
            val world = world()
            world.hooks.has(A_PROJECT_DIR, ProjectHooksState.PRESENT)
            val gate = world.hooks.holdStatus(A_PROJECT_DIR)
            val controller = start(world)
            requestRemove(controller)

            act { controller.onEvent(SidebarEvent.UninstallHooks) }
            gate.complete(Unit)
            act { }

            world.uninstalls().shouldBeEmpty()
            controller.state.value.dialog shouldBe confirm(ProjectHooksState.PRESENT)
        }

    @Test
    fun `a second uninstall click and a dismiss while it runs change nothing`() =
        runTest {
            val world = world()
            world.hooks.has(A_PROJECT_DIR, ProjectHooksState.PRESENT)
            val gate = world.hooks.holdUninstall(A_PROJECT_DIR)
            val controller = start(world)
            requestRemove(controller)
            act { controller.onEvent(SidebarEvent.UninstallHooks) }

            act { controller.onEvent(SidebarEvent.UninstallHooks) }
            act { controller.onEvent(SidebarEvent.Dismiss) }
            controller.state.value.dialog shouldBe Dialog.Waiting.BUSY
            gate.complete(Unit)
            act { }

            world.uninstalls() shouldBe listOf("uninstall $A_PROJECT_DIR")
            controller.state.value.dialog shouldBe
                confirm(ProjectHooksState.ABSENT, notice = UiText("remove.hooks.uninstalled"))
        }

    @Test
    fun `uninstall failure logs only the project id and the category`() =
        runTest {
            val secret = "secret-user"
            val dir = Path.of("/Users/$secret/projects/$secret-name")
            val own = aProject(id = "project0003", dir = dir, displayName = "$secret-name")
            val world =
                ControllerWorld(projects = FakeProjectsApi(initial = listOf(own)), running = emptyList())
            world.hooks.has(dir, ProjectHooksState.PRESENT)
            world.hooks.failNextUninstall(ProjectHooksError.WRITE_FAILED)
            val controller = start(world)
            act { controller.onEvent(SidebarEvent.Select(own.id)) }
            act { controller.onIntent(ProjectsIntent.RemoveSelectedRequested) }

            act { controller.onEvent(SidebarEvent.UninstallHooks) }

            val warning = world.logger.entriesAt(LogLevel.WARN).single()
            warning.message shouldBe "project hooks uninstall failed"
            warning.fields["projectId"] shouldBe "project0003"
            warning.fields.keys shouldBe setOf("projectId", "reason")
            (warning.fields["reason"] is String) shouldBe true
            world.logger.entries.forEach { entry ->
                entry.message shouldNotContain secret
                entry.fields.values.forEach { it.toString() shouldNotContain secret }
                entry.cause shouldBe null
            }
        }

    @Test
    fun `remove after uninstall removes the project`() =
        runTest {
            val world = world()
            world.hooks.has(A_PROJECT_DIR, ProjectHooksState.PRESENT)
            val controller = start(world)
            requestRemove(controller)
            act { controller.onEvent(SidebarEvent.UninstallHooks) }

            act { controller.onEvent(SidebarEvent.RemoveKeepSessions) }

            world.projects.calls shouldBe listOf("remove project0001")
            controller.state.value.dialog shouldBe null
            controller.state.value.projects shouldBe listOf(other)
        }

    @Test
    fun `stop and remove after uninstall still stops the sessions`() =
        runTest {
            val world = world(listOf(working))
            world.hooks.has(A_PROJECT_DIR, ProjectHooksState.PRESENT)
            val controller = start(world)
            requestRemove(controller)
            act { controller.onEvent(SidebarEvent.UninstallHooks) }

            act { controller.onEvent(SidebarEvent.StopAndRemove) }

            world.sessions.calls shouldBe listOf("stopAllIn project0001")
            world.projects.calls shouldBe listOf("remove project0001")
            controller.state.value.dialog shouldBe null
        }
}
