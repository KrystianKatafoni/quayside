@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.agentruntime.api.FakeWorkspaceTrust
import dev.quayside.agentruntime.api.WorkspaceTrust
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.missioncontrol.ui.NewAgentWorld.QUAYSIDE
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionCommands
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
    protected val focuses = mutableListOf<ProjectFocus>()
    protected val first = SessionId("00000001")
    protected val refused = SessionError.Runtime(RuntimeProblem.COMMAND_FAILED)

    /** `claude --bg` refuses the directory: it is not trusted in Claude Code (AM-309). */
    protected val trusting = SessionError.Runtime(RuntimeProblem.WORKSPACE_NOT_TRUSTED)
    protected val trust = FakeWorkspaceTrust()

    protected fun TestScope.controller(
        commands: SessionCommands = RecordingCommands(fake),
        projects: FakeProjectsApi = NewAgentWorld.projects(),
        port: WorkspaceTrust = trust,
    ): NewAgentController =
        NewAgentController(NewAgentPorts(commands, projects, port), backgroundScope, log) { focus, id ->
            started += focus.project to id
            focuses += focus
        }

    protected fun TestScope.opened(
        commands: SessionCommands = RecordingCommands(fake),
        project: ProjectId? = QUAYSIDE.id,
        port: WorkspaceTrust = trust,
    ): NewAgentController = controller(commands, port = port).also { it.open(project) }

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
