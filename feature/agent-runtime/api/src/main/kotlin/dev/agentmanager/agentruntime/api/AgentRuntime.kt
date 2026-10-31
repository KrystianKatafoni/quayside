package dev.agentmanager.agentruntime.api

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.domain.ShellCommand
import java.nio.file.Path
import java.time.Instant

/**
 * Claude Code conversation UUID: `session_id` in hook payloads and `sessionId` in `claude agents --json`.
 * It joins hook events with the sessions found by polling.
 */
@JvmInline
public value class ConversationId(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) { "ConversationId must not be blank" }
    }
}

/**
 * Port to the Claude Code supervisor (Agent View, ADR 0002).
 *
 * Every `suspend` function may be called from any dispatcher (the implementation switches to IO
 * itself), never blocks the calling thread and never throws except
 * [kotlin.coroutines.cancellation.CancellationException]; failures come back as [RuntimeError].
 */
public interface AgentRuntime {
    /**
     * Lists the supervisor's sessions whose `cwd` equals [under] or lies below it, compared by path
     * components (`/p/ab` is not under `/p/a`); `null` lists every session.
     */
    public suspend fun listSessions(under: Path? = null): Outcome<List<RuntimeSession>, RuntimeError>

    /** Starts a background agent (`claude --bg`) described by [spec] and returns the supervisor's short id. */
    public suspend fun start(spec: StartSpec): Outcome<SessionId, RuntimeError>

    /** Stops the session; it stays on the list as [RuntimePhase.STOPPED]. */
    public suspend fun stop(id: SessionId): Outcome<Unit, RuntimeError>

    /** Restarts a stopped session in the same conversation; it comes back as [RuntimePhase.WORKING]. */
    public suspend fun respawn(id: SessionId): Outcome<Unit, RuntimeError>

    /** Removes the session from the supervisor list; never deletes project files. */
    public suspend fun remove(id: SessionId): Outcome<Unit, RuntimeError>

    /**
     * Tail of the session output (`claude logs`), already truncated by the implementation.
     * The text is sensitive (ENG-C8): log it at DEBUG level only.
     */
    public suspend fun recentOutput(id: SessionId): Outcome<String, RuntimeError>

    /** Pure: the command a terminal runs in a PTY to attach to [id]. Does not check that [id] exists. */
    public fun attachCommand(id: SessionId): ShellCommand

    /** Checks the local Claude Code installation. Never fails: every problem is listed in [RuntimeHealth]. */
    public suspend fun health(): RuntimeHealth
}

/**
 * One session as the supervisor reports it.
 *
 * @property id short supervisor id, e.g. `7c5dcf5d`.
 * @property conversationId conversation UUID, null until the supervisor reports it.
 * @property name display name of the session.
 * @property cwd working directory the agent runs in.
 * @property phase what the agent is doing now.
 * @property waitingFor what a [RuntimePhase.BLOCKED] session waits for; null otherwise.
 * @property startedAt when the session was started.
 * @property pid process id of a running agent; null when it is not running.
 */
public data class RuntimeSession(
    val id: SessionId,
    val conversationId: ConversationId?,
    val name: String,
    val cwd: Path,
    val phase: RuntimePhase,
    val waitingFor: WaitingFor?,
    val startedAt: Instant,
    val pid: Long?,
)

/** Lifecycle phase of a supervised session. */
public enum class RuntimePhase {
    /** The agent is working on its turn. */
    WORKING,

    /** The agent waits for the user, see [RuntimeSession.waitingFor]. */
    BLOCKED,

    /** The agent finished its turn. */
    DONE,

    /** The agent ended with an error. */
    FAILED,

    /** The session was stopped and can be respawned. */
    STOPPED,
}

/** Reason a [RuntimePhase.BLOCKED] session waits for the user. */
public sealed interface WaitingFor {
    /** A tool permission prompt. */
    public data object Permission : WaitingFor

    /** A question or the next prompt. */
    public data object Input : WaitingFor

    /** A reason this version does not know; [raw] is the supervisor's value. */
    public data class Other(
        val raw: String,
    ) : WaitingFor
}

/**
 * What to start as a background agent (FR-S3). Optional texts are either absent or not blank.
 * [toString] hides [prompt], because it may hold sensitive content (ENG-C8).
 *
 * @property cwd working directory of the agent.
 * @property prompt first prompt of the conversation.
 * @property name display name; the supervisor picks one when null.
 * @property agent name of a custom agent (`--agent`).
 * @property model model alias or name (`--model`).
 * @property permissionMode permission mode (`--permission-mode`); CLI default when null.
 */
public data class StartSpec(
    val cwd: Path,
    val prompt: String,
    val name: String? = null,
    val agent: String? = null,
    val model: String? = null,
    val permissionMode: PermissionMode? = null,
) {
    init {
        require(prompt.isNotBlank()) { "StartSpec prompt must not be blank" }
        require(name?.isNotBlank() ?: true) { "StartSpec name must not be blank" }
        require(agent?.isNotBlank() ?: true) { "StartSpec agent must not be blank" }
        require(model?.isNotBlank() ?: true) { "StartSpec model must not be blank" }
    }

    override fun toString(): String =
        "StartSpec(cwd=$cwd, prompt=***, name=$name, agent=$agent, model=$model, permissionMode=$permissionMode)"
}

/** Claude Code permission mode an agent starts with. */
public enum class PermissionMode {
    /** Ask before tools that need permission. */
    DEFAULT,

    /** Accept file edits without asking. */
    ACCEPT_EDITS,

    /** Plan only, no changes. */
    PLAN,

    /** Skip every permission prompt. */
    BYPASS_PERMISSIONS,
}

/**
 * State of the local Claude Code installation (NFR-9).
 *
 * @property version raw `claude --version` result; null when Claude Code is not installed.
 * @property agentViewEnabled whether Agent View (background sessions) is switched on.
 * @property supervisorRunning whether the supervisor answers.
 * @property problems everything that stops the runtime from working; empty when ready.
 */
public data class RuntimeHealth(
    val version: String?,
    val agentViewEnabled: Boolean,
    val supervisorRunning: Boolean,
    val problems: List<RuntimeError>,
) {
    /** True when there are no [problems]. */
    val isReady: Boolean get() = problems.isEmpty()
}

/** Why a runtime operation failed. Never carries input text beyond fixed categories and tails. */
public sealed interface RuntimeError {
    /** No `claude` binary on the login shell `PATH`. */
    public data object NotInstalled : RuntimeError

    /** Claude Code is older than [required]; [found] is the raw installed version. */
    public data class UnsupportedVersion(
        val found: String,
        val required: String,
    ) : RuntimeError

    /** Agent View (background sessions) is switched off. */
    public data object AgentViewDisabled : RuntimeError

    /** The supervisor does not know session [id]. */
    public data class UnknownSession(
        val id: SessionId,
    ) : RuntimeError

    /** The CLI ended with [exitCode]; [stderrTail] is the truncated end of its stderr. */
    public data class CommandFailed(
        val exitCode: Int,
        val stderrTail: String,
    ) : RuntimeError

    /** The CLI output could not be read; [detail] is a fixed reason category, never input text. */
    public data class Unparseable(
        val detail: String,
    ) : RuntimeError

    /** The CLI did not answer in time. */
    public data object Timeout : RuntimeError
}
