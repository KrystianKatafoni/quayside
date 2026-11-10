package dev.quayside.sessions.api

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId

/** Why a [SessionCommands] operation or [SessionMonitor.recentOutput] failed. Never carries input text (ENG-C8). */
public sealed interface SessionError {
    /** No registered project has [id]. */
    public data class UnknownProject(
        val id: ProjectId,
    ) : SessionError

    /**
     * The supervisor does not know session [id]; also the answer for a command that does not apply to a
     * [SessionOrigin.DISK] session (respawn, output) and for a disk session that is hidden or gone.
     */
    public data class UnknownSession(
        val id: SessionId,
    ) : SessionError

    /** Session [id] is active; stop it first. */
    public data class SessionActive(
        val id: SessionId,
    ) : SessionError

    /** [stopped] sessions were stopped, the sessions in [failed] still run. */
    public data class StopIncomplete(
        val stopped: Int,
        val failed: List<SessionId>,
    ) : SessionError

    /** The subfolder of a start cannot be used; never carries its name (ENG-C8). */
    public data class SubfolderUnavailable(
        val project: ProjectId,
        val problem: SubfolderProblem,
    ) : SessionError

    /** The Claude Code runtime failed with [problem]. */
    public data class Runtime(
        val problem: RuntimeProblem,
    ) : SessionError
}

/** Fixed categories of runtime failures, 1:1 with the runtime's errors except an unknown session; no texts (ENG-C8). */
public enum class RuntimeProblem {
    /** Claude Code is not installed. */
    NOT_INSTALLED,

    /** Claude Code is older than required. */
    UNSUPPORTED_VERSION,

    /** Agent View (background sessions) is switched off. */
    AGENT_VIEW_DISABLED,

    /** The CLI ended with an error. */
    COMMAND_FAILED,

    /** The CLI output could not be read. */
    UNPARSEABLE,

    /** The CLI did not answer in time. */
    TIMEOUT,

    /** Claude Code refused to start in a directory it does not trust. */
    WORKSPACE_NOT_TRUSTED,
}

/** Why a subfolder of a start cannot be used (AM-308). */
public enum class SubfolderProblem {
    /** Not one of the project's subfolders (also a path such as `../x` or a different letter case). */
    NOT_IN_PROJECT,

    /** One of them, but not a directory on disk (missing, a file or a symlink). */
    MISSING,
}
