package dev.quayside.missioncontrol.ui

import dev.quayside.agentruntime.api.WorkspaceTrust
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.info
import dev.quayside.core.domain.warn
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.sessions.api.NewSessionSpec
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface NewAgentEvent {
    data class Edit(
        val form: NewAgentForm,
    ) : NewAgentEvent

    data object Submit : NewAgentEvent

    data object Cancel : NewAgentEvent

    /** The user agreed to trust the directory of the consent step. */
    data object TrustAccept : NewAgentEvent

    /** The user declined to trust the directory of the consent step. */
    data object TrustDecline : NewAgentEvent
}

/** The ports of [NewAgentController] in one type (ENG-C2: at most five parameters). */
public data class NewAgentPorts(
    val commands: SessionCommands,
    val projects: ProjectsApi,
    val trust: WorkspaceTrust,
)

/**
 * Logic of the "Nowy agent" dialog (FR-S3). Methods run on Main; [SessionCommands.start] runs in [scope] (the window
 * scope), one start at a time. On success closes and calls [onStarted]. Logs only ids and fixed categories, never the
 * prompt, the name or the agent (ENG-C8). Never throws.
 */
public class NewAgentController(
    ports: NewAgentPorts,
    private val scope: CoroutineScope,
    private val logger: Logger,
    private val onStarted: (ProjectId, SessionId) -> Unit,
) {
    private val commands = ports.commands
    private val projects = ports.projects
    private val current = MutableStateFlow<NewAgentState?>(null)
    private val open = MutableStateFlow(false)
    private val trustFlow = NewAgentTrustFlow(current, ports.trust, scope, logger, ::run)

    /** Null = closed. */
    internal val state: StateFlow<NewAgentState?> = current

    /** For the palette and the menu: true while the dialog is open. */
    public val isOpen: StateFlow<Boolean> = open

    /** Opens with [project] preselected (null: the user picks); ignored when open or with no projects. */
    public fun open(project: ProjectId?) {
        val list = projects.projects.value
        if (current.value != null || list.isEmpty()) return
        show(NewAgentState(NewAgentForm(NewAgentRules.defaultProject(project, list)), list))
    }

    internal fun onEvent(event: NewAgentEvent) {
        when (event) {
            is NewAgentEvent.Edit -> edit(event.form)
            NewAgentEvent.Submit -> submit()
            NewAgentEvent.Cancel -> cancel()
            NewAgentEvent.TrustAccept -> trustFlow.accept()
            NewAgentEvent.TrustDecline -> trustFlow.decline()
        }
    }

    /** The fields are fixed while a start runs and in the consent step (the restart uses the form as it is). */
    private fun edit(form: NewAgentForm) {
        current.update { shown ->
            if (shown == null || shown.busy || shown.trust != null) shown else shown.copy(form = form)
        }
    }

    private fun show(next: NewAgentState?) {
        current.value = next
        open.value = next != null
    }

    /** In the consent step Cancel means "no"; otherwise it closes the dialog unless a start runs. */
    private fun cancel() {
        if (current.value?.trust != null) {
            trustFlow.decline()
        } else if (current.value?.busy == false) {
            show(null)
        }
    }

    /** Every user submit forgets an earlier trust write, so a new refusal asks again. */
    private fun submit() {
        val shown = current.value?.takeUnless { it.busy || it.trust != null } ?: return
        when (val spec = NewAgentRules.specOf(shown.form)) {
            is Outcome.Err -> current.value = shown.copy(problem = spec.error)
            is Outcome.Ok -> start(shown.copy(trustTried = false), spec.value.first, spec.value.second)
        }
    }

    /** [NewAgentState.busy] is set before the launch, so a second `Submit` is ignored; `finally` releases it. */
    private fun start(
        shown: NewAgentState,
        project: ProjectId,
        spec: NewSessionSpec,
    ) {
        current.value = shown.copy(busy = true, problem = null)
        scope.launch { run(project, spec) }
    }

    private suspend fun run(
        project: ProjectId,
        spec: NewSessionSpec,
    ) {
        try {
            when (val result = commands.start(project, spec)) {
                is Outcome.Ok -> started(project, result.value)
                is Outcome.Err -> failed(project, result.error)
            }
        } finally {
            current.update { it?.copy(busy = false) }
        }
    }

    private fun started(
        project: ProjectId,
        id: SessionId,
    ) {
        logger.info("new agent: started", "session" to id.value, "project" to project.value)
        show(null)
        onStarted(project, id)
    }

    private fun failed(
        project: ProjectId,
        error: SessionError,
    ) {
        val problem = (error as? SessionError.Runtime)?.problem
        logger.warn("new agent: start failed", "error" to error::class.simpleName, "problem" to problem?.name)
        val reason = NewAgentProblem.StartFailed(NewAgentRules.failureOf(error))
        val untrusted = problem == RuntimeProblem.WORKSPACE_NOT_TRUSTED
        current.update { shown ->
            val dir = shown?.projects?.firstOrNull { it.id == project }?.dir
            when {
                shown == null -> null
                untrusted && dir != null -> shown.refusedBy(dir)
                else -> shown.copy(form = shown.form.forgetUnknownProject(error), problem = reason)
            }
        }
    }
}
