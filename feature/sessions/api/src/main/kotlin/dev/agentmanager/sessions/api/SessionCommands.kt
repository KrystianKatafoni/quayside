package dev.agentmanager.sessions.api

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.domain.SessionId

/**
 * Write side of the sessions feature (ENG-I2).
 *
 * Every `suspend` function may be called from any dispatcher, never blocks the calling thread and
 * never throws except [kotlin.coroutines.cancellation.CancellationException] (ENG-I4, ENG-I5).
 * Every successful command is visible in [SessionMonitor.snapshot] before it returns.
 */
public interface SessionCommands {
    /**
     * Starts a background agent in the directory of [project] (FR-S3). The new session is in
     * [SessionMonitor.snapshot] before `start` returns and is [SessionState.Working] until the
     * supervisor reports another state. Errors: [SessionError.UnknownProject], [SessionError.Runtime].
     */
    public suspend fun start(
        project: ProjectId,
        spec: NewSessionSpec,
    ): Outcome<SessionId, SessionError>

    /**
     * Stops the session (FR-S4). Idempotent: a session that is not active is left as it is.
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
     * [SessionState.Working]. Errors: [SessionError.UnknownSession], [SessionError.SessionActive],
     * [SessionError.Runtime].
     */
    public suspend fun respawn(id: SessionId): Outcome<Unit, SessionError>

    /**
     * Removes an inactive session from the list; never deletes project files.
     * Errors: [SessionError.UnknownSession], [SessionError.SessionActive], [SessionError.Runtime].
     */
    public suspend fun remove(id: SessionId): Outcome<Unit, SessionError>
}

/**
 * What to start (FR-S3). Optional texts are either absent or not blank; a blank [prompt] or a blank
 * optional text is rejected with [IllegalArgumentException]. [toString] shows [prompt] as `***`,
 * because it may hold sensitive content (ENG-C8).
 *
 * @property prompt first prompt of the conversation.
 * @property name display name; the supervisor picks one when null.
 * @property agent name of a custom agent.
 * @property model model alias or name.
 * @property permissionMode permission mode; the Claude Code default when null.
 */
public data class NewSessionSpec(
    val prompt: String,
    val name: String? = null,
    val agent: String? = null,
    val model: String? = null,
    val permissionMode: SessionPermissionMode? = null,
) {
    init {
        require(prompt.isNotBlank()) { "NewSessionSpec prompt must not be blank" }
        require(name?.isNotBlank() ?: true) { "NewSessionSpec name must not be blank" }
        require(agent?.isNotBlank() ?: true) { "NewSessionSpec agent must not be blank" }
        require(model?.isNotBlank() ?: true) { "NewSessionSpec model must not be blank" }
    }

    override fun toString(): String =
        "NewSessionSpec(prompt=$HIDDEN, name=$name, agent=$agent, model=$model, permissionMode=$permissionMode)"
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
