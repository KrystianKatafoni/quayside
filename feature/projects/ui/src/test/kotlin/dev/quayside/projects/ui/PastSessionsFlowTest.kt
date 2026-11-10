@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.projects.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.HistoryAge
import dev.quayside.sessions.api.HistoryLimit
import dev.quayside.sessions.api.HistorySettingsError
import dev.quayside.sessions.api.WorldPastSession
import dev.quayside.sessions.api.WorldProject
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * After adding a folder the sidebar waits for the first scan of the new project and, when it found old sessions, asks
 * once whether to show them (FR-P9, AM-312). The scan is held back (`FakeSessions.pauseScan`) so the tests decide when
 * its result arrives. Nothing is stored per project.
 */
class PastSessionsFlowTest {
    private val first = ProjectId("project0001")
    private val second = ProjectId("project0002")
    private val otherDir: Path = Path.of("/Users/test/projects/other")

    private fun file(
        n: Int,
        cwd: Path = A_PROJECT_DIR,
    ) = WorldPastSession(
        SessionId("00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"),
        cwd,
        T0.minusSeconds(n * 60L),
    )

    private fun world(vararg files: WorldPastSession) =
        ControllerWorld(
            extraProjects = listOf(WorldProject(first, A_PROJECT_DIR), WorldProject(second, otherDir)),
            past = PastFiles(files.toList()),
        )

    private fun TestScope.addFolder(
        world: ControllerWorld,
        controller: ProjectsController,
        dir: Path = A_PROJECT_DIR,
    ) {
        world.picker.willPick(dir)
        act { controller.onIntent(ProjectsIntent.AddRequested) }
        act { controller.onEvent(SidebarEvent.ConfirmAdd) }
    }

    private fun TestScope.resume(world: ControllerWorld) = act { world.sessions.resumeScan() }

    private val firstProject = Project(first, A_PROJECT_DIR, "agent-manager")

    @Test
    fun `dialog after add when the scan finds sessions`() =
        runTest {
            val world = world(file(1), file(2), file(3))
            val controller = start(world)

            addFolder(world, controller)
            controller.state.value.dialog
                .shouldBeNull()
            resume(world)

            controller.state.value.dialog shouldBe
                Dialog.PastSessions(firstProject, PastCounts(total = 3, inSubfolders = null, inRoot = null))
        }

    @Test
    fun `no dialog when the scan finds zero, also when a file appears later`() =
        runTest {
            val world = world()
            val controller = start(world)
            addFolder(world, controller)

            resume(world)
            act { world.sessions.addPastSession(file(1)) }

            controller.state.value.dialog
                .shouldBeNull()
        }

    @Test
    fun `no dialog for sessions of another project`() =
        runTest {
            val world = world(file(1, cwd = otherDir))
            val controller = start(world)
            addFolder(world, controller)

            resume(world)

            controller.state.value.dialog
                .shouldBeNull()
        }

    @Test
    fun `still waits just before 30 s`() =
        runTest {
            val world = world(file(1))
            val controller = start(world)
            addFolder(world, controller)

            advanceTimeBy(29.seconds)
            resume(world)

            controller.state.value.dialog shouldBe
                Dialog.PastSessions(firstProject, PastCounts(total = 1, inSubfolders = null, inRoot = null))
        }

    @Test
    fun `waiting ends after 30 s`() =
        runTest {
            val world = world(file(1))
            val controller = start(world)
            addFolder(world, controller)

            advanceTimeBy(30.seconds + 1.seconds)
            runCurrent()
            resume(world)

            controller.state.value.dialog
                .shouldBeNull()
        }

    @Test
    fun `other dialog abandons the wait`() =
        runTest {
            val world = world(file(1))
            val controller = start(world)
            addFolder(world, controller)
            act { controller.onEvent(SidebarEvent.RenameRequested(first)) }

            resume(world)
            (controller.state.value.dialog is Dialog.Rename) shouldBe true
            act { controller.onEvent(SidebarEvent.Dismiss) }

            controller.state.value.dialog
                .shouldBeNull()
        }

    @Test
    fun `a dialog opened and closed before the scan still abandons the wait`() =
        runTest {
            val world = world(file(1))
            val controller = start(world)
            addFolder(world, controller)
            act { controller.onEvent(SidebarEvent.RenameRequested(first)) }
            act { controller.onEvent(SidebarEvent.Dismiss) }

            resume(world)

            controller.state.value.dialog
                .shouldBeNull()
        }

    @Test
    fun `removing the project abandons the wait`() =
        runTest {
            val world = world(file(1))
            val controller = start(world)
            addFolder(world, controller)

            world.projects.remove(first)
            runCurrent()
            resume(world)

            controller.state.value.dialog
                .shouldBeNull()
        }

    @Test
    fun `late snapshot after reopening the add dialog is ignored`() =
        runTest {
            // Given folder A waits for its scan and the user adds folder B meanwhile
            val world = world(file(1), file(2, cwd = otherDir), file(3, cwd = otherDir))
            val controller = start(world)
            addFolder(world, controller)
            addFolder(world, controller, otherDir)

            // When the scan finishes for both
            resume(world)

            // Then only the question about B is asked
            controller.state.value.dialog shouldBe
                Dialog.PastSessions(
                    Project(second, otherDir, "other"),
                    PastCounts(total = 2, inSubfolders = null, inRoot = null),
                )
        }

    @Test
    fun `Pokaż selects the project and asks for the finished filter`() =
        runTest {
            val world = world(file(1))
            val controller = start(world)
            addFolder(world, controller)
            resume(world)

            act { controller.onEvent(SidebarEvent.ShowPastSessions) }

            controller.state.value.dialog
                .shouldBeNull()
            controller.state.value.selected shouldBe first
            world.shown shouldBe listOf(first)
        }

    @Test
    fun `Nie teraz closes the dialog and stores nothing`() =
        runTest {
            val world = world(file(1))
            val controller = start(world)
            addFolder(world, controller)
            resume(world)

            act { controller.onEvent(SidebarEvent.Dismiss) }
            act { world.sessions.addPastSession(file(2)) }

            controller.state.value.dialog
                .shouldBeNull()
            world.shown.shouldBeEmpty()
            world.historySettings.updates shouldBe 0
        }

    @Test
    fun `sidebar badges ignore disk sessions`() =
        runTest {
            val world = world(file(1), file(2))
            val controller = start(world)
            addFolder(world, controller)
            resume(world)

            controller.state.value.sessions.counts.values
                .sumOf { it.active } shouldBe 0
            controller.state.value.sessions.fresh shouldBe true
        }

    @Test
    fun `the add dialog offers the saved global choices`() =
        runTest {
            val world = world()
            val controller = start(world)
            world.picker.willPick(A_PROJECT_DIR)

            act { controller.onIntent(ProjectsIntent.AddRequested) }

            (controller.state.value.dialog as Dialog.ConfirmAdd).history shouldBe
                HistoryChoice(HistoryAge.DAYS_30, HistoryLimit.SESSIONS_200)
        }

    @Test
    fun `add dialog choice is saved as the global setting at once`() =
        runTest {
            val world = world()
            val controller = start(world)
            world.picker.willPick(A_PROJECT_DIR)
            act { controller.onIntent(ProjectsIntent.AddRequested) }

            act { controller.onEvent(SidebarEvent.ChooseHistoryAge(HistoryAge.DAYS_7)) }
            act { controller.onEvent(SidebarEvent.ChooseHistoryLimit(HistoryLimit.SESSIONS_1000)) }

            world.historySettings.settings.value.maxAge shouldBe HistoryAge.DAYS_7
            world.historySettings.settings.value.maxPerProject shouldBe HistoryLimit.SESSIONS_1000
            (controller.state.value.dialog as Dialog.ConfirmAdd).history shouldBe
                HistoryChoice(HistoryAge.DAYS_7, HistoryLimit.SESSIONS_1000)
            world.projects.calls.shouldBeEmpty()
        }

    @Test
    fun `a choice stays saved when the add dialog is cancelled`() =
        runTest {
            val world = world()
            val controller = start(world)
            world.picker.willPick(A_PROJECT_DIR)
            act { controller.onIntent(ProjectsIntent.AddRequested) }
            act { controller.onEvent(SidebarEvent.ChooseHistoryAge(HistoryAge.UNLIMITED)) }

            act { controller.onEvent(SidebarEvent.Dismiss) }
            world.picker.willPick(A_PROJECT_DIR)
            act { controller.onIntent(ProjectsIntent.AddRequested) }

            (controller.state.value.dialog as Dialog.ConfirmAdd).history?.maxAge shouldBe HistoryAge.UNLIMITED
        }

    @Test
    fun `a failed save keeps the previous choice in the dialog`() =
        runTest {
            val world = world()
            val controller = start(world)
            world.picker.willPick(A_PROJECT_DIR)
            act { controller.onIntent(ProjectsIntent.AddRequested) }
            world.historySettings.failNext(HistorySettingsError.WRITE_FAILED)

            act { controller.onEvent(SidebarEvent.ChooseHistoryAge(HistoryAge.DAYS_7)) }

            (controller.state.value.dialog as Dialog.ConfirmAdd).history?.maxAge shouldBe HistoryAge.DAYS_30
            world.historySettings.settings.value.maxAge shouldBe HistoryAge.DAYS_30
        }

    @Test
    fun `a choice event without the add dialog saves nothing`() =
        runTest {
            val world = world()
            val controller = start(world)

            act { controller.onEvent(SidebarEvent.ChooseHistoryAge(HistoryAge.DAYS_7)) }

            world.historySettings.updates shouldBe 0
        }
}
