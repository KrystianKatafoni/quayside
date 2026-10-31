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
 * a whole [Session] at DEBUG level only.
 *
 * @property id short supervisor id, e.g. `7c5dcf5d`.
 * @property project the deepest registered project whose directory contains [cwd], compared by path
 *   components (see [SessionRules.projectFor]); null when no project contains it.
 * @property name display name of the session.
 * @property cwd working directory the agent runs in.
 * @property state what the agent is doing now.
 * @property startedAt when the session was started.
 * @property lastActivityAt last state change or hook event (FR-S1).
 * @property summary short summary of the session; null when unknown.
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
) {
    override fun toString(): String =
        "Session(id=$id, project=$project, name=$name, cwd=$cwd, state=$state, startedAt=$startedAt, " +
            "lastActivityAt=$lastActivityAt, summary=${summary?.let { HIDDEN }})"
}

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
