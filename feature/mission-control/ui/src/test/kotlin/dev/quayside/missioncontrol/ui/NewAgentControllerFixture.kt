@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.missioncontrol.ui.NewAgentWorld.QUAYSIDE
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/**
 * The world of the controller tests: fake sessions behind a [RecordingCommands], a [RecordingLogger] and the list of
 * the sessions reported by `onStarted`. Time never moves; the controller runs in `backgroundScope`.
 */
@Suppress("AbstractClassCanBeConcreteClass") // a base for two test classes; JUnit builds them, not this class
internal abstract class NewAgentControllerFixture {
    protected val fake: FakeSessions = NewAgentWorld.sessions()
    protected val log = RecordingLogger()
    protected val started = mutableListOf<Pair<ProjectId, SessionId>>()
    protected val first = SessionId("00000001")
    protected val trusting = SessionError.Runtime(RuntimeProblem.COMMAND_FAILED)

    protected fun TestScope.controller(
        commands: RecordingCommands = RecordingCommands(fake),
        projects: FakeProjectsApi = NewAgentWorld.projects(),
    ): NewAgentController =
        NewAgentController(commands, projects, backgroundScope, log) { project, id -> started += project to id }

    protected fun TestScope.opened(
        commands: RecordingCommands = RecordingCommands(fake),
        project: ProjectId? = QUAYSIDE.id,
    ): NewAgentController = controller(commands).also { it.open(project) }

    protected fun TestScope.submit(controller: NewAgentController) {
        controller.onEvent(NewAgentEvent.Submit)
        runCurrent()
    }

    protected fun NewAgentController.shown(): NewAgentState = state.value.shouldNotBeNull()

    protected fun NewAgentController.edit(
        prompt: String = "fix the login",
        name: String = "",
    ) = onEvent(NewAgentEvent.Edit(shown().form.copy(prompt = prompt, name = name)))
}
