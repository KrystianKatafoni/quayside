package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.SubfolderName
import dev.quayside.sessions.api.NewSessionSpec
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionPermissionMode
import dev.quayside.sessions.api.SubfolderProblem

/** Model aliases of the CLI; [alias] null = Claude Code default (no flag). */
internal enum class ModelChoice(
    val alias: String?,
) {
    DEFAULT(null),
    SONNET("sonnet"),
    OPUS("opus"),
    HAIKU("haiku"),
}

/**
 * Form of the "Nowy agent" dialog. [permission] null = Claude Code default (no flag); [subfolder] null = the project
 * directory (only a group has subfolders). [toString] hides [prompt],
 * [name], [agent] and [subfolder]: they are user data and never reach a log (ENG-C8).
 */
internal data class NewAgentForm(
    val project: ProjectId?,
    val prompt: String = "",
    val name: String = "",
    val agent: String = "",
    val model: ModelChoice = ModelChoice.DEFAULT,
    val permission: SessionPermissionMode? = null,
    val subfolder: SubfolderName? = null,
) {
    override fun toString(): String =
        "NewAgentForm(project=$project, prompt=<hidden>, name=<hidden>, agent=<hidden>, " +
            "model=$model, permission=$permission, subfolder=<hidden>)"
}

/** The open dialog: the [form], the registered [projects] to pick from, a running start ([busy]) and a [problem]. */
internal data class NewAgentState(
    val form: NewAgentForm,
    val projects: List<Project>,
    val busy: Boolean = false,
    val problem: NewAgentProblem? = null,
)

/** What is wrong with the form or why the start failed; shown under the form. */
internal sealed interface NewAgentProblem {
    data object NoProject : NewAgentProblem

    data object EmptyPrompt : NewAgentProblem

    data object PromptTooLong : NewAgentProblem

    data object NameInvalid : NewAgentProblem

    data object AgentInvalid : NewAgentProblem

    /** [reason] is a fixed text per error kind; it never quotes the input. */
    data class StartFailed(
        val reason: UiText,
    ) : NewAgentProblem
}

/** Pure rules of the dialog (AM-608 rules 1, 2 and 5). */
internal object NewAgentRules {
    /** The prompt goes into argv (ARG_MAX 1 MiB), so it is limited. In code points, after `trim()`. */
    const val MAX_PROMPT = 100_000
    const val MAX_NAME = 80

    private val agentPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")

    /**
     * The project and the spec of [form], or the first problem in the order: project, prompt, name, subagent.
     * A spec is built only after the checks, so its `require` never throws.
     */
    fun specOf(form: NewAgentForm): Outcome<Pair<ProjectId, NewSessionSpec>, NewAgentProblem> {
        val project = form.project ?: return Outcome.Err(NewAgentProblem.NoProject)
        val problem = problemOf(form)
        return if (problem == null) Outcome.Ok(project to specFor(form)) else Outcome.Err(problem)
    }

    /**
     * The project and subfolder to preselect for the sidebar [selected] focus: its project when it is on the list,
     * and its subfolder only when that project still has it; else the project directory.
     */
    fun defaultFocus(
        selected: ProjectFocus?,
        projects: List<Project>,
    ): Pair<ProjectId?, SubfolderName?> {
        val project = projects.firstOrNull { it.id == selected?.project } ?: return null to null
        return project.id to selected?.subfolder?.takeIf { it in project.subfolders }
    }

    /** [form] for project [id]; another project than the form's clears the subfolder. */
    fun withProject(
        form: NewAgentForm,
        id: ProjectId,
    ): NewAgentForm = if (id == form.project) form else form.copy(project = id, subfolder = null)

    /** Fixed text per error kind; never the id, the prompt or `stderr`. */
    fun failureOf(error: SessionError): UiText =
        UiText(
            when (error) {
                is SessionError.UnknownProject -> "newAgent.failure.unknownProject"
                is SessionError.Runtime -> runtimeKey(error.problem)
                is SessionError.SubfolderUnavailable -> subfolderKey(error.problem)
                else -> "newAgent.failure.other"
            },
        )

    private fun subfolderKey(problem: SubfolderProblem): String =
        when (problem) {
            SubfolderProblem.NOT_IN_PROJECT -> "newAgent.failure.subfolder.notInProject"
            SubfolderProblem.MISSING -> "newAgent.failure.subfolder.missing"
        }

    private fun runtimeKey(problem: RuntimeProblem): String =
        when (problem) {
            RuntimeProblem.NOT_INSTALLED -> "newAgent.failure.notInstalled"
            RuntimeProblem.UNSUPPORTED_VERSION -> "newAgent.failure.unsupportedVersion"
            RuntimeProblem.AGENT_VIEW_DISABLED -> "newAgent.failure.agentViewDisabled"
            RuntimeProblem.COMMAND_FAILED -> "newAgent.failure.commandFailed"
            RuntimeProblem.TIMEOUT, RuntimeProblem.UNPARSEABLE -> "newAgent.failure.unknownResult"
        }

    private fun problemOf(form: NewAgentForm): NewAgentProblem? {
        val prompt = form.prompt.trim()
        val name = form.name.trim()
        return when {
            prompt.isEmpty() -> NewAgentProblem.EmptyPrompt
            prompt.codePointCount(0, prompt.length) > MAX_PROMPT -> NewAgentProblem.PromptTooLong
            !nameValid(name) -> NewAgentProblem.NameInvalid
            !agentValid(form.agent.trim()) -> NewAgentProblem.AgentInvalid
            else -> null
        }
    }

    private fun nameValid(name: String): Boolean =
        name.isEmpty() ||
            (name.codePointCount(0, name.length) <= MAX_NAME && '\n' !in name && '\r' !in name && !name.startsWith('-'))

    private fun agentValid(agent: String): Boolean = agent.isEmpty() || agentPattern.matches(agent)

    private fun specFor(form: NewAgentForm): NewSessionSpec =
        NewSessionSpec(
            prompt = form.prompt.trim(),
            name = form.name.trim().ifEmpty { null },
            agent = form.agent.trim().ifEmpty { null },
            model = form.model.alias,
            permissionMode = form.permission,
            subfolder = form.subfolder?.value,
        )
}
