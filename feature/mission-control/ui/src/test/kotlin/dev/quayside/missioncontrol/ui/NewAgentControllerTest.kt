@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.missioncontrol.ui.NewAgentWorld.OTHER
import dev.quayside.missioncontrol.ui.NewAgentWorld.PROJECTS
import dev.quayside.missioncontrol.ui.NewAgentWorld.QUAYSIDE
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.NewSessionSpec
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionPermissionMode
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Logic of the "Nowy agent" dialog on `FakeSessions` behind a [RecordingCommands] (FR-S3, AM-608 rules 1 and 4):
 * opening, editing, validating and the result of a start.
 */
internal class NewAgentControllerTest : NewAgentControllerFixture() {
    @Test
    fun `starts closed`() =
        runTest {
            val controller = controller()

            controller.state.value.shouldBeNull()
            controller.isOpen.value shouldBe false
        }

    @Test
    fun `open preselects the selected project`() =
        runTest {
            val controller = controller()

            controller.open(OTHER.id)

            controller.state.value shouldBe NewAgentState(NewAgentForm(OTHER.id), PROJECTS)
            controller.isOpen.value shouldBe true
        }

    @Test
    fun `open without a selected project lets the user pick`() =
        runTest {
            val controller = controller()

            controller.open(null)

            controller
                .shown()
                .form.project
                .shouldBeNull()
            controller.shown().projects shouldBe PROJECTS
        }

    @Test
    fun `open with a project that is gone lets the user pick`() =
        runTest {
            val controller = controller()

            controller.open(ProjectId("removed"))

            controller
                .shown()
                .form.project
                .shouldBeNull()
        }

    @Test
    fun `open does nothing without projects`() =
        runTest {
            val controller = controller(projects = FakeProjectsApi())

            controller.open(QUAYSIDE.id)

            controller.state.value.shouldBeNull()
            controller.isOpen.value shouldBe false
        }

    @Test
    fun `open is ignored while the dialog is open`() =
        runTest {
            val controller = opened()
            controller.edit(prompt = "keep me")

            controller.open(OTHER.id)

            controller.shown().form shouldBe NewAgentForm(QUAYSIDE.id, prompt = "keep me")
        }

    @Test
    fun `edit replaces the form`() =
        runTest {
            val controller = opened()
            val form =
                NewAgentForm(OTHER.id, "p", "n", "a", ModelChoice.HAIKU, SessionPermissionMode.ACCEPT_EDITS)

            controller.onEvent(NewAgentEvent.Edit(form))

            controller.shown().form shouldBe form
        }

    @Test
    fun `edit and submit while closed do nothing`() =
        runTest {
            val commands = RecordingCommands(fake)
            val controller = controller(commands)

            controller.onEvent(NewAgentEvent.Edit(NewAgentForm(QUAYSIDE.id, "x")))
            controller.onEvent(NewAgentEvent.Cancel)
            submit(controller)

            controller.state.value.shouldBeNull()
            commands.specs.shouldBeEmpty()
            fake.calls.shouldBeEmpty()
        }

    @Test
    fun `submit starts with the form values`() =
        runTest {
            val commands = RecordingCommands(fake)
            val controller = opened(commands)
            val form =
                NewAgentForm(
                    OTHER.id,
                    prompt = " fix the login ",
                    name = "login",
                    agent = "code-reviewer",
                    model = ModelChoice.OPUS,
                    permission = SessionPermissionMode.PLAN,
                )
            controller.onEvent(NewAgentEvent.Edit(form))

            submit(controller)

            commands.specs shouldBe
                listOf(
                    OTHER.id to
                        NewSessionSpec("fix the login", "login", "code-reviewer", "opus", SessionPermissionMode.PLAN),
                )
            fake.calls shouldBe listOf("start other")
        }

    @Test
    fun `submit with an empty prompt shows the problem and starts nothing`() =
        runTest {
            val commands = RecordingCommands(fake)
            val controller = opened(commands)

            submit(controller)

            controller.shown().problem shouldBe NewAgentProblem.EmptyPrompt
            controller.shown().busy shouldBe false
            controller.shown().form shouldBe NewAgentForm(QUAYSIDE.id)
            commands.specs.shouldBeEmpty()
            fake.calls.shouldBeEmpty()
        }

    @Test
    fun `submit without a project shows the problem and starts nothing`() =
        runTest {
            val commands = RecordingCommands(fake)
            val controller = opened(commands, project = null)
            controller.edit()

            submit(controller)

            controller.shown().problem shouldBe NewAgentProblem.NoProject
            commands.specs.shouldBeEmpty()
        }

    @Test
    fun `a corrected form starts after a validation problem`() =
        runTest {
            val commands = RecordingCommands(fake)
            val controller = opened(commands)
            submit(controller)
            controller.edit()

            submit(controller)

            commands.specs.size shouldBe 1
            controller.state.value.shouldBeNull()
        }

    @Test
    fun `success closes and reports the new session`() =
        runTest {
            val controller = opened()
            controller.edit()

            submit(controller)

            started shouldBe listOf(QUAYSIDE.id to first)
            controller.state.value.shouldBeNull()
            controller.isOpen.value shouldBe false
        }

    @Test
    fun `failure keeps the form and shows the reason`() =
        runTest {
            val controller = opened()
            controller.edit(prompt = "keep me", name = "keep-name")
            val form = controller.shown().form
            fake.failNext(refused)

            submit(controller)

            controller.state.value shouldBe
                NewAgentState(
                    form,
                    PROJECTS,
                    busy = false,
                    problem = NewAgentProblem.StartFailed(NewAgentRules.failureOf(refused)),
                )
            started.shouldBeEmpty()
        }

    @Test
    fun `unknown project failure clears the project so the user picks again`() =
        runTest {
            val controller = opened()
            controller.edit()
            fake.failNext(SessionError.UnknownProject(QUAYSIDE.id))

            submit(controller)

            controller
                .shown()
                .form.project
                .shouldBeNull()
            controller.shown().form.prompt shouldBe "fix the login"
            controller.shown().problem shouldBe
                NewAgentProblem.StartFailed(NewAgentRules.failureOf(SessionError.UnknownProject(QUAYSIDE.id)))
            controller.shown().busy shouldBe false
        }

    @Test
    fun `a failed start can be retried`() =
        runTest {
            val commands = RecordingCommands(fake)
            val controller = opened(commands)
            controller.edit()
            fake.failNext(refused)
            submit(controller)
            controller.shown().problem.shouldNotBeNull()

            submit(controller)

            commands.specs.size shouldBe 2
            fake.calls shouldBe listOf("start quayside", "start quayside")
            started shouldBe listOf(QUAYSIDE.id to first)
            controller.state.value.shouldBeNull()
        }
}
