package dev.quayside.sessions.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId

/**
 * Write side of the sessions feature (ENG-I2).
 *
 * Every `suspend` function may be called from any dispatcher, never blocks the calling thread and
 * never throws except [kotlin.coroutines.cancellation.CancellationException] (ENG-I4, ENG-I5).
 * Every successful command is visible in [SessionMonitor.snapshot] before it returns.
 */
public interface SessionCommands {
    /**
     * Starts a background agent in the directory of [project], or in its [NewSessionSpec.subfolder] (FR-S3,
     * FR-P7). The new session is in [SessionMonitor.snapshot] before `start` returns and is
     * [SessionState.Working] until the supervisor reports another state. Errors: [SessionError.UnknownProject],
     * [SessionError.SubfolderUnavailable], [SessionError.Runtime].
     *
     * The session belongs to the deepest registered project that contains its `cwd`: a subfolder that is also
     * registered as a separate project puts the session in that project, not in the group.
     *
     * @throws IllegalArgumentException never from `start` itself; it comes from building [NewSessionSpec]
     *   when [NewSessionSpec.subfolder] is empty or blank.
     */
    public suspend fun start(
        project: ProjectId,
        spec: NewSessionSpec,
    ): Outcome<SessionId, SessionError>

    /**
     * Stops the session (FR-S4). Idempotent: a session that is not active is left as it is, also a
     * [SessionOrigin.DISK] one (`Ok`, the supervisor is not called).
     * Errors: [SessionError.UnknownSession], [SessionError.Runtime].
     */
    public suspend fun stop(id: SessionId): Outcome<Unit, SessionError>

    /**
     * Stops every active session attributed to [project] (FR-P4), by the same rule as
     * [SessionsSnapshot.countsFor], from a fresh listing rather than the possibly stale snapshot.
     * Tries every session and returns how many it stopped.
     *
     * Errors: [SessionError.UnknownProject]; [SessionError.StopIncomplete] when some sessions could
     * not be stopped and still run; [SessionError.Runtime] when the listing failed.
     */
    public suspend fun stopAllIn(project: ProjectId): Outcome<Int, SessionError>

    /**
     * Brings a [SessionState.Stopped], [SessionState.Done] or [SessionState.Failed] session back as
     * [SessionState.Working]. A [SessionOrigin.DISK] session is not the supervisor's: `UnknownSession`, the
     * supervisor is not called; use [resume] for it. Errors: [SessionError.UnknownSession], [SessionError.SessionActive],
     * [SessionError.Runtime].
     */
    public suspend fun respawn(id: SessionId): Outcome<Unit, SessionError>

    /**
     * Resumes one [SessionOrigin.DISK] session in its start directory ([Session.cwd], FR-P9); never several at once.
     * Before `resume` returns, the snapshot shows the live session under [ResumedSession.id] (waiting for a prompt) and
     * no longer the disk one, unless it is a copy. A [SessionOrigin.SUPERVISOR] session: [SessionError.UnknownSession],
     * use [respawn]. Checks before Claude Code is called: the session file is still on the disk, the start directory
     * exists. Errors: [SessionError.UnknownSession], [SessionError.ResumeUnavailable], [SessionError.Runtime].
     */
    public suspend fun resume(id: SessionId): Outcome<ResumedSession, SessionError>

    /**
     * Removes an inactive session from the list; never deletes project files. A [SessionOrigin.DISK] session is
     * hidden for good (its UUID is saved in [SessionHistorySettings.hidden]); its session file is never deleted,
     * a hide that could not be saved is [SessionError.Runtime] with [RuntimeProblem.COMMAND_FAILED], and (like every
     * command that checks the session first) it needs a successful listing of the supervisor.
     * Errors: [SessionError.UnknownSession], [SessionError.SessionActive], [SessionError.Runtime].
     */
    public suspend fun remove(id: SessionId): Outcome<Unit, SessionError>

    /**
     * Puts session [id] in [place] inside its project, or back to the place by its directory when [place] is null
     * (FR-P9, ADR 0033). Any origin and state; never calls Claude Code; checks the session in the current snapshot.
     * Saved under the conversation, so it survives a [resume]; [Session.assignedDir] shows it before `assignPlace`
     * returns. Null without a saved place writes nothing. Errors: [SessionError.UnknownSession] (also a session
     * outside every project), [SessionError.SubfolderUnavailable] with [SubfolderProblem.NOT_IN_PROJECT] (not exactly
     * one of `Project.subfolders`, e.g. `Soft` for `soft` or `../x`), [SessionError.Runtime] with
     * [RuntimeProblem.COMMAND_FAILED] when saving failed (the place stays as it was).
     */
    public suspend fun assignPlace(
        id: SessionId,
        place: PlaceAssignment?,
    ): Outcome<Unit, SessionError>
}

/** A place the user picks for a session inside its project (AM-314). */
public sealed interface PlaceAssignment {
    /** The project directory itself ("folder główny"). */
    public data object ProjectRoot : PlaceAssignment

    /**
     * One of `Project.subfolders` by its exact name; not blank. [toString] hides [name] (ENG-C8).
     *
     * @throws IllegalArgumentException when [name] is blank (a programmer error, like [NewSessionSpec.subfolder]).
     */
    public data class Subfolder(
        val name: String,
    ) : PlaceAssignment {
        init {
            require(name.isNotBlank()) { "PlaceAssignment.Subfolder name must not be blank" }
        }

        override fun toString(): String = "Subfolder(name=$HIDDEN)"
    }
}

/**
 * Result of [SessionCommands.resume].
 *
 * @property id the live session.
 * @property copy true: the conversation was open elsewhere, a copy runs and the disk session stays on the list
 *   unchanged (docs/compat.md R4).
 */
public data class ResumedSession(
    val id: SessionId,
    val copy: Boolean,
)

/**
 * What to start (FR-S3). Optional texts are either absent or not blank; a blank [prompt] or a blank
 * optional text (also [subfolder]) is rejected with [IllegalArgumentException]. [toString] shows [prompt]
 * and [subfolder] as `***`, because they may hold sensitive content (ENG-C8).
 *
 * @property prompt first prompt of the conversation.
 * @property name display name; the supervisor picks one when null.
 * @property agent name of a custom agent.
 * @property model model alias or name.
 * @property permissionMode permission mode; the Claude Code default when null.
 * @property subfolder name of one of the project's subfolders (`Project.subfolders`, FR-P7) to start in;
 *  null = the project directory. Not blank. User data: [toString] shows it as `***` (ENG-C8).
 */
public data class NewSessionSpec(
    val prompt: String,
    val name: String? = null,
    val agent: String? = null,
    val model: String? = null,
    val permissionMode: SessionPermissionMode? = null,
    val subfolder: String? = null,
) {
    init {
        require(prompt.isNotBlank()) { "NewSessionSpec prompt must not be blank" }
        require(name?.isNotBlank() ?: true) { "NewSessionSpec name must not be blank" }
        require(agent?.isNotBlank() ?: true) { "NewSessionSpec agent must not be blank" }
        require(model?.isNotBlank() ?: true) { "NewSessionSpec model must not be blank" }
        require(subfolder?.isNotBlank() ?: true) { "NewSessionSpec subfolder must not be blank" }
    }

    override fun toString(): String =
        "NewSessionSpec(prompt=$HIDDEN, name=$name, agent=$agent, model=$model, permissionMode=$permissionMode, " +
            "subfolder=${subfolder?.let { HIDDEN }})"
}

/** Claude Code permission mode a session starts with. */
public enum class SessionPermissionMode {
    /** Ask before tools that need permission. */
    DEFAULT,

    /** Accept file edits without asking. */
    ACCEPT_EDITS,

    /** Plan only, no changes. */
    PLAN,

    /** Skip every permission prompt. */
    BYPASS_PERMISSIONS,
}
