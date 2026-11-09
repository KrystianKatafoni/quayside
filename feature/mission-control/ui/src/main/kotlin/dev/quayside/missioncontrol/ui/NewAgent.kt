package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.NewSessionSpec
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionPermissionMode
import java.nio.file.Path

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
 * Form of the "Nowy agent" dialog. [permission] null = Claude Code default (no flag). [toString] hides [prompt],
 * [name] and [agent]: they are user data and never reach a log (ENG-C8).
 */
internal data class NewAgentForm(
    val project: ProjectId?,
    val prompt: String = "",
    val name: String = "",
    val agent: String = "",
    val model: ModelChoice = ModelChoice.DEFAULT,
    val permission: SessionPermissionMode? = null,
) {
    override fun toString(): String =
        "NewAgentForm(project=$project, prompt=<hidden>, name=<hidden>, agent=<hidden>, " +
            "model=$model, permission=$permission)"
}

/**
 * The open dialog: the [form], the registered [projects] to pick from, a running start ([busy]) and a [problem].
 * [trust] non-null = the consent step "Zaufać katalogowi?" instead of the fields; [trustTried] = trust was written
 * during this attempt, so a second refusal is not asked again (AM-309 rule 4).
 */
internal data class NewAgentState(
    val form: NewAgentForm,
    val projects: List<Project>,
    val busy: Boolean = false,
    val problem: NewAgentProblem? = null,
    val trust: TrustStep? = null,
    val trustTried: Boolean = false,
)

/** The consent step for [dir]; [saving] while `WorkspaceTrust.trust` runs. */
internal data class TrustStep(
    val dir: Path,
    val saving: Boolean = false,
)

/** What is wrong with the form or why the start failed; shown under the form. */
internal sealed interface NewAgentProblem {
    data object NoProject : NewAgentProblem

    data object EmptyPrompt : NewAgentProblem

    data object PromptTooLong : NewAgentProblem

    data object NameInvalid : NewAgentProblem

    data object AgentInvalid : NewAgentProblem

    /** The user declined to trust [dir]; the manual instruction is shown. */
    data class NotTrusted(
        val dir: Path,
    ) : NewAgentProblem

    /** Writing the trust for [dir] failed; the manual instruction is shown. */
    data class TrustFailed(
        val dir: Path,
    ) : NewAgentProblem

    /** Trust for [dir] was written, but `claude --bg` still refuses it. */
    data class TrustIneffective(
        val dir: Path,
    ) : NewAgentProblem

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

    /** The selected project when it is on the list, else none (the user picks). */
    fun defaultProject(
        selected: ProjectId?,
        projects: List<Project>,
    ): ProjectId? = selected?.takeIf { id -> projects.any { it.id == id } }

    /** Fixed text per error kind; never the id, the prompt or `stderr`. */
    fun failureOf(error: SessionError): UiText =
        UiText(
            when (error) {
                is SessionError.UnknownProject -> "newAgent.failure.unknownProject"
                is SessionError.Runtime -> runtimeKey(error.problem)
                else -> "newAgent.failure.other"
            },
        )

    private fun runtimeKey(problem: RuntimeProblem): String =
        when (problem) {
            RuntimeProblem.NOT_INSTALLED -> "newAgent.failure.notInstalled"

            RuntimeProblem.UNSUPPORTED_VERSION -> "newAgent.failure.unsupportedVersion"

            RuntimeProblem.AGENT_VIEW_DISABLED -> "newAgent.failure.agentViewDisabled"

            RuntimeProblem.COMMAND_FAILED -> "newAgent.failure.commandFailed"

            // Shown only when the controller cannot ask (the project left the list); otherwise it asks to trust.
            RuntimeProblem.WORKSPACE_NOT_TRUSTED -> "newAgent.failure.workspaceNotTrusted"

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
        )
}

/** Text of a problem under the form; a path is always an argument (`{0}`), never part of the text. */
internal fun NewAgentProblem.text(): UiText =
    when (this) {
        NewAgentProblem.NoProject -> {
            UiText("newAgent.problem.noProject")
        }

        NewAgentProblem.EmptyPrompt -> {
            UiText("newAgent.problem.emptyPrompt")
        }

        NewAgentProblem.PromptTooLong -> {
            UiText("newAgent.problem.promptTooLong")
        }

        NewAgentProblem.NameInvalid -> {
            UiText("newAgent.problem.nameInvalid")
        }

        NewAgentProblem.AgentInvalid -> {
            UiText("newAgent.problem.agentInvalid")
        }

        is NewAgentProblem.NotTrusted -> {
            UiText("newAgent.problem.notTrusted", listOf(dir.toString()))
        }

        is NewAgentProblem.TrustFailed -> {
            UiText("newAgent.problem.trustFailed", listOf(dir.toString()))
        }

        is NewAgentProblem.TrustIneffective -> {
            UiText("newAgent.problem.trustIneffective", listOf(dir.toString()))
        }

        is NewAgentProblem.StartFailed -> {
            reason
        }
    }

/** The start was refused as untrusted: ask about [dir], or, after a trust write in this attempt, explain. */
internal fun NewAgentState.refusedBy(dir: Path): NewAgentState =
    if (trustTried) {
        copy(
            problem = NewAgentProblem.TrustIneffective(dir),
        )
    } else {
        copy(trust = TrustStep(dir), problem = null)
    }

/** The user declined the consent step: back to the form with the manual instruction. */
internal fun NewAgentState.declined(): NewAgentState =
    copy(trust = null, problem = trust?.let { NewAgentProblem.NotTrusted(it.dir) } ?: problem)

/** An unknown project is cleared from the form so the user picks again. */
internal fun NewAgentForm.forgetUnknownProject(error: SessionError): NewAgentForm =
    if (error is SessionError.UnknownProject) copy(project = null) else this
