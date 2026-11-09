@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.missioncontrol.ui.NewAgentWorld.ANNA
import dev.quayside.missioncontrol.ui.NewAgentWorld.CARL
import dev.quayside.missioncontrol.ui.NewAgentWorld.GROUP
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.SubfolderName
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SubfolderProblem
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The "Nowy agent" controller with a group: the subfolder of the focus, the start and a refused start (AM-308). */
internal class NewAgentSubfolderControllerTest : NewAgentControllerFixture() {
    private fun TestScope.openedAt(
        focus: ProjectFocus?,
        commands: RecordingCommands = RecordingCommands(fake),
    ): NewAgentController =
        controller(commands, NewAgentWorld.projectsWithGroup()).also {
            it.open(focus)
            // The tests below are about what happens to a preselected subfolder: it must be there first.
            it.shown().form.subfolder shouldBe focus?.subfolder
            it.edit()
        }

    @Test
    fun `open with a subfolder focus preselects it`() =
        runTest {
            val controller = controller(projects = NewAgentWorld.projectsWithGroup())

            controller.open(ProjectFocus(GROUP.id, ANNA))

            controller.shown().form shouldBe NewAgentForm(GROUP.id, subfolder = ANNA)
        }

    @Test
    fun `open with a subfolder the group no longer has starts in the project directory`() =
        runTest {
            val controller = controller(projects = NewAgentWorld.projectsWithGroup())

            controller.open(ProjectFocus(GROUP.id, SubfolderName("removed")))

            controller.shown().form shouldBe NewAgentForm(GROUP.id)
        }

    @Test
    fun `start sends the subfolder and reports its focus`() =
        runTest {
            val commands = RecordingCommands(fake)
            val controller = openedAt(ProjectFocus(GROUP.id, ANNA), commands)

            submit(controller)

            commands.specs.map { it.first to it.second.subfolder } shouldBe listOf(GROUP.id to "Anna")
            focuses shouldBe listOf(ProjectFocus(GROUP.id, ANNA))
            started shouldBe listOf(GROUP.id to first)
            controller.state.value.shouldBeNull()
        }

    @Test
    fun `start without a subfolder reports the project focus`() =
        runTest {
            val commands = RecordingCommands(fake)
            val controller = openedAt(ProjectFocus(GROUP.id), commands)

            submit(controller)

            commands.specs.map { it.second.subfolder } shouldBe listOf(null)
            focuses shouldBe listOf(ProjectFocus(GROUP.id))
        }

    @Test
    fun `a subfolder that is gone from disk keeps the form and says so`() =
        runTest {
            val controller = openedAt(ProjectFocus(GROUP.id, CARL))
            val form = controller.shown().form

            submit(controller)

            controller.shown().form shouldBe form
            controller.shown().problem shouldBe
                NewAgentProblem.StartFailed(UiText("newAgent.failure.subfolder.missing"))
            focuses.shouldBeEmpty()
        }

    @Test
    fun `missing subfolder error keeps the form`() =
        runTest {
            val controller = openedAt(ProjectFocus(GROUP.id, ANNA))
            val form = controller.shown().form
            val error = SessionError.SubfolderUnavailable(GROUP.id, SubfolderProblem.MISSING)
            fake.failNext(error)

            submit(controller)

            controller.shown().form shouldBe form
            controller.shown().problem shouldBe NewAgentProblem.StartFailed(NewAgentRules.failureOf(error))
        }

    @Test
    fun `not in project error clears the subfolder and keeps the project`() =
        runTest {
            val controller = openedAt(ProjectFocus(GROUP.id, ANNA))
            controller.edit(prompt = "keep me", name = "keep-name")
            val error = SessionError.SubfolderUnavailable(GROUP.id, SubfolderProblem.NOT_IN_PROJECT)
            fake.failNext(error)

            submit(controller)

            controller.shown().form shouldBe NewAgentForm(GROUP.id, prompt = "keep me", name = "keep-name")
            controller.shown().problem shouldBe NewAgentProblem.StartFailed(NewAgentRules.failureOf(error))
            controller.shown().busy shouldBe false
        }

    @Test
    fun `unknown project error clears the subfolder with the project`() =
        runTest {
            val controller = openedAt(ProjectFocus(GROUP.id, ANNA))
            fake.failNext(SessionError.UnknownProject(GROUP.id))

            submit(controller)

            controller.shown().form shouldBe NewAgentForm(null, prompt = "fix the login")
        }

    @Test
    fun `logs never carry the subfolder name`() =
        runTest {
            val controller = openedAt(ProjectFocus(GROUP.id, ANNA))
            fake.failNext(SessionError.SubfolderUnavailable(GROUP.id, SubfolderProblem.MISSING))
            submit(controller)

            submit(controller)

            log.dump().contains(ANNA.value) shouldBe false
        }
}
