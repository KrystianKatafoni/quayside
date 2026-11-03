package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.info
import dev.quayside.core.domain.warn
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.sessions.api.NewSessionSpec
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
}

/**
 * Logic of the "Nowy agent" dialog (FR-S3). Methods run on Main; [SessionCommands.start] runs in [scope] (the window
 * scope), one start at a time. On success closes and calls [onStarted]. Logs only ids and fixed categories, never the
 * prompt, the name or the agent (ENG-C8). Never throws.
 */
public class NewAgentController(
    private val commands: SessionCommands,
    private val projects: ProjectsApi,
    private val scope: CoroutineScope,
    private val logger: Logger,
    private val onStarted: (ProjectId, SessionId) -> Unit,
) {
    private val current = MutableStateFlow<NewAgentState?>(null)
    private val open = MutableStateFlow(false)

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
            NewAgentEvent.Cancel -> if (current.value?.busy == false) show(null)
        }
    }

    private fun edit(form: NewAgentForm) {
        current.update { shown -> if (shown == null || shown.busy) shown else shown.copy(form = form) }
    }

    private fun show(next: NewAgentState?) {
        current.value = next
        open.value = next != null
    }

    private fun submit() {
        val shown = current.value?.takeUnless { it.busy } ?: return
        when (val spec = NewAgentRules.specOf(shown.form)) {
            is Outcome.Err -> current.value = shown.copy(problem = spec.error)
            is Outcome.Ok -> start(shown, spec.value.first, spec.value.second)
        }
    }

    /** [NewAgentState.busy] is set before the launch, so a second `Submit` is ignored; `finally` releases it. */
    private fun start(
        shown: NewAgentState,
        project: ProjectId,
        spec: NewSessionSpec,
    ) {
        current.value = shown.copy(busy = true, problem = null)
        scope.launch {
            try {
                when (val result = commands.start(project, spec)) {
                    is Outcome.Ok -> started(project, result.value)
                    is Outcome.Err -> failed(result.error)
                }
            } finally {
                current.update { it?.copy(busy = false) }
            }
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

    private fun failed(error: SessionError) {
        val problem = (error as? SessionError.Runtime)?.problem?.name
        logger.warn("new agent: start failed", "error" to error::class.simpleName, "problem" to problem)
        val reason = NewAgentProblem.StartFailed(NewAgentRules.failureOf(error))
        current.update { shown ->
            shown?.let {
                val form = if (error is SessionError.UnknownProject) it.form.copy(project = null) else it.form
                it.copy(form = form, problem = reason)
            }
        }
    }
}
