package dev.quayside.sessions.api

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import java.nio.file.Path
import java.time.Instant

/**
 * One session as Quayside sees it.
 *
 * [summary] (FR-S7), [cwd] and [name] are user data: log them at DEBUG level only (ENG-C8).
 * `toString` shows [summary] as `***` but, like `RuntimeSession`, prints [cwd] and [name], so log
 * a whole [Session] at DEBUG level only. For [SessionOrigin.DISK] the [name] is the name from the session file
 * (`custom-title`), else the first user message, else the start of the UUID: conversation content, so also DEBUG only.
 *
 * @property id short supervisor id, e.g. `7c5dcf5d`; for [SessionOrigin.DISK] the conversation UUID.
 * @property project the deepest registered project whose directory contains [cwd], compared by path
 *   components (see [SessionRules.projectFor]); null when no project contains it. The place inside the group
 *   of the project comes from [placeDir], not from [cwd] alone.
 * @property name display name of the session; for [SessionOrigin.DISK]: the name from the file, then the first user
 *   message, then 8 characters of the UUID.
 * @property cwd working directory the agent runs in.
 * @property state what the agent is doing now.
 * @property startedAt when the session was started.
 * @property lastActivityAt last state change or hook event (FR-S1).
 * @property summary short summary of the session; null when unknown. For [SessionOrigin.DISK]: the first user
 *   message, or "first → last" user message when they differ; null for a file without a user message.
 * @property meta model, permission mode and git branch (FR-S8); every part may be unknown.
 * @property origin where the session comes from (FR-P9).
 * @property assignedDir directory the user put the session in (FR-P9, AM-314, ADR 0033): the directory of its
 *   project or one of `Project.subfolders`; null = the place follows [cwd]. Never changes [project]. User data.
 */
public data class Session(
    val id: SessionId,
    val project: ProjectId?,
    val name: String,
    val cwd: Path,
    val state: SessionState,
    val startedAt: Instant,
    val lastActivityAt: Instant,
    val summary: String?,
    val meta: SessionMeta = SessionMeta(),
    val origin: SessionOrigin = SessionOrigin.SUPERVISOR,
    val assignedDir: Path? = null,
) {
    /** Directory that decides the place of the session inside its project's group: [assignedDir], else [cwd]. */
    val placeDir: Path get() = assignedDir ?: cwd

    override fun toString(): String =
        "Session(id=$id, project=$project, name=$name, cwd=$cwd, state=$state, startedAt=$startedAt, " +
            "lastActivityAt=$lastActivityAt, summary=${summary?.let { HIDDEN }}, meta=$meta, " +
            "assignedDir=$assignedDir)"
}

/** Where a [Session] comes from (FR-P9, ADR 0032). */
public enum class SessionOrigin {
    /** Listed by the Claude Code supervisor; [Session.id] is its short id. */
    SUPERVISOR,

    /**
     * Read from a session file only; always [SessionState.Stopped], [Session.id] is the conversation UUID, and only
     * [SessionCommands.remove] (hides it), [SessionCommands.resume] and [SessionCommands.assignPlace] apply; every
     * other command gives
     * [SessionError.UnknownSession], except that [SessionCommands.stop] answers `Ok` without a call.
     */
    DISK,
}

/**
 * What the details panel shows besides the supervisor's fields (FR-S8). Every part is null while Quayside does not
 * know it: the monitor reads them from the Claude Code transcript and the work tree, so a brand-new session or a
 * directory outside a repository has gaps. Values are shown as they are, never interpreted.
 *
 * [branch] is user data (ENG-C8): log a whole [SessionMeta] at DEBUG level only.
 *
 * @property model model id of the last answer, e.g. `claude-opus-4-1`.
 * @property permissionMode Claude Code permission mode, e.g. `default`, `acceptEdits`, `plan`, `bypassPermissions`.
 * @property branch checked-out git branch of the session directory; the first 7 characters of the commit when `HEAD`
 *   is detached.
 */
public data class SessionMeta(
    val model: String? = null,
    val permissionMode: String? = null,
    val branch: String? = null,
)

/**
 * What a session is doing (FR-S2). [isActive] is the single "active session" rule: the agent keeps
 * running (supervisor `WORKING` or `BLOCKED`).
 */
public sealed interface SessionState {
    /** True for [Working], [AwaitingInput] and [AwaitingPermission]. */
    public val isActive: Boolean
        get() =
            when (this) {
                Working, is AwaitingInput, is AwaitingPermission -> true
                Done, Failed, Stopped -> false
            }

    /** True for [AwaitingInput] and [AwaitingPermission]: the agent waits for the user. */
    public val isWaiting: Boolean get() = this is AwaitingInput || this is AwaitingPermission

    /** The agent is working on its turn. */
    public data object Working : SessionState

    /**
     * The agent waits for a reply or the next prompt. [question] is user data (ENG-C8); `toString`
     * shows it as `***`.
     */
    public data class AwaitingInput(
        val question: String?,
    ) : SessionState {
        override fun toString(): String = "AwaitingInput(question=${question?.let { HIDDEN }})"
    }

    /** The agent asks for permission to use [tool]; null when the tool is unknown. */
    public data class AwaitingPermission(
        val tool: String?,
    ) : SessionState

    /** The agent finished its turn. */
    public data object Done : SessionState

    /** The agent ended with an error. */
    public data object Failed : SessionState

    /** The session was stopped and can be respawned. */
    public data object Stopped : SessionState
}

/** Placeholder `toString` prints instead of user data (ENG-C8). */
internal const val HIDDEN = "***"
