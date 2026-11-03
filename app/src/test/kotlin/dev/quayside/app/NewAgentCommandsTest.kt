@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.app

import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.testing.TestClock
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.NewAgentController
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectInspector
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.projects.ui.DirectoryPicker
import dev.quayside.projects.ui.ProjectsController
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/** The palette command "Nowy agent…" with ⌘N and its guard against open dialogs (FR-S3, FR-K1, AM-608, ADR 0011). */
class NewAgentCommandsTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")
    private var opened = 0

    private fun offered(
        hasProjects: Boolean = true,
        dialogOpen: Boolean = false,
    ): List<Command> = newAgentCommands(hasProjects, dialogOpen) { opened++ }

    @Test
    fun `offers new agent with cmd n when projects exist`() =
        runTest {
            val command = offered().single()

            command.id shouldBe CommandId("sessions.new")
            command.title shouldBe Strings["command.newAgent"]
            command.group shouldBe CommandGroup.SESSION
            command.keywords shouldBe listOf("new", "agent", "start", "nowy")
            command.shortcut shouldBe AppShortcuts.NEW_AGENT
        }

    @Test
    fun `offers nothing without projects`() {
        offered(hasProjects = false).shouldBeEmpty()
    }

    @Test
    fun `offers nothing while a dialog is open`() {
        offered(dialogOpen = true).shouldBeEmpty()
    }

    @Test
    fun `running the command opens the dialog once`() =
        runTest {
            offered().single().run()

            opened shouldBe 1
        }

    private class Parts(
        val projects: FakeProjectsApi,
        val controller: ProjectsController,
        val missionControl: MissionControlController,
        val newAgent: NewAgentController,
    )

    private fun TestScope.parts(initial: List<dev.quayside.projects.api.Project>): Parts {
        val fake = FakeSessions(SessionsWorld(projects = listOf(WorldProject(project.id, project.dir))))
        val api = SessionsApi(monitor = fake, commands = fake)
        val projects = FakeProjectsApi(initial = initial)
        val controller =
            ProjectsController(
                projects,
                FakeProjectInspector(projects = projects),
                api,
                DirectoryPicker {
                    null
                },
                backgroundScope,
                NoOpLogger,
            )
        val missionControl =
            MissionControlController(
                api,
                projects,
                controller.selection,
                TestClock(),
                backgroundScope,
                NoOpLogger,
                1.hours,
            )
        val newAgent = NewAgentController(fake, projects, backgroundScope, NoOpLogger) { _, _ -> }
        return Parts(projects, controller, missionControl, newAgent)
    }

    private fun TestScope.provider(
        parts: Parts,
        otherDialogOpen: MutableStateFlow<Boolean> = MutableStateFlow(false),
    ): NewAgentCommands =
        NewAgentCommands(
            parts.controller,
            parts.missionControl,
            parts.projects,
            parts.newAgent,
            backgroundScope,
            otherDialogOpen,
        ).also { runCurrent() }

    @Test
    fun `the provider offers the command once a project exists`() =
        runTest {
            val parts = parts(initial = emptyList())
            val provider = provider(parts)
            provider.commands.value.shouldBeEmpty()

            parts.projects.add(project.dir)
            runCurrent()

            provider.commands.value.map { it.id } shouldBe listOf(CommandId("sessions.new"))
        }

    @Test
    fun `running the provider command opens the dialog`() =
        runTest {
            val parts = parts(initial = listOf(project))
            val provider = provider(parts)
            parts.newAgent.isOpen.value shouldBe false

            provider.commands.value
                .single()
                .run()
            runCurrent()

            parts.newAgent.isOpen.value shouldBe true
        }

    @Test
    fun `the provider hides the command while the dialog is open`() =
        runTest {
            val parts = parts(initial = listOf(project))
            val provider = provider(parts)
            parts.newAgent.open(project.id)
            runCurrent()

            provider.commands.value.shouldBeEmpty()
        }

    @Test
    fun `the provider hides the command while another dialog is open`() =
        runTest {
            val parts = parts(initial = listOf(project))
            val other = MutableStateFlow(value = false)
            val provider = provider(parts, other)
            provider.commands.value.size shouldBe 1

            other.value = true
            runCurrent()

            provider.commands.value.shouldBeEmpty()
        }
}
