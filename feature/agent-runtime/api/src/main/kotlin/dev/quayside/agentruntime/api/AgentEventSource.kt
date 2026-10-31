package dev.quayside.agentruntime.api

import kotlinx.coroutines.flow.Flow
import java.nio.file.Path
import java.time.Instant

/**
 * Source of Claude Code hook events (ADR 0004).
 */
public interface AgentEventSource {
    /**
     * Hot stream of hook events. There is no replay: events emitted while nobody collects are lost,
     * and polling through [AgentRuntime.listSessions] corrects the state.
     */
    public val events: Flow<AgentEvent>
}

/**
 * What every hook event carries.
 *
 * @property conversationId conversation the event belongs to.
 * @property cwd working directory reported by the hook.
 * @property at when Quayside received the event.
 */
public data class EventMeta(
    val conversationId: ConversationId,
    val cwd: Path,
    val at: Instant,
)

/**
 * Event sent by a Claude Code hook. Free texts ([NeedsInput.question], [TurnEnded.lastMessage]) are
 * sensitive (ENG-C8): log them at DEBUG level only; `toString` shows them as `***`.
 */
public sealed interface AgentEvent {
    /** Common data of the event. */
    public val meta: EventMeta

    /** The agent waits for an answer (`Notification` hook); [question] when the hook provides it. */
    public data class NeedsInput(
        override val meta: EventMeta,
        val question: String?,
    ) : AgentEvent {
        override fun toString(): String = "NeedsInput(meta=$meta, question=${question?.let { HIDDEN }})"
    }

    /** The agent asks for permission to use [tool] (`Notification` hook). */
    public data class PermissionRequested(
        override val meta: EventMeta,
        val tool: String?,
    ) : AgentEvent

    /** The agent finished its turn (`Stop` hook); [lastMessage] when the hook provides it. */
    public data class TurnEnded(
        override val meta: EventMeta,
        val lastMessage: String?,
    ) : AgentEvent {
        override fun toString(): String = "TurnEnded(meta=$meta, lastMessage=${lastMessage?.let { HIDDEN }})"
    }

    /** The turn ended with an error (`StopFailure` hook); [kind] is the error category. */
    public data class Failed(
        override val meta: EventMeta,
        val kind: String,
    ) : AgentEvent

    /** The session ended (`SessionEnd` hook) for [reason]. */
    public data class SessionEnded(
        override val meta: EventMeta,
        val reason: String,
    ) : AgentEvent
}

/** Placeholder `toString` prints instead of sensitive free text (ENG-C8). */
private const val HIDDEN = "***"
