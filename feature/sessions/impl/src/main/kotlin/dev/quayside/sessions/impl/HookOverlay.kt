package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.AgentEvent
import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.agentruntime.api.WaitingFor
import dev.quayside.core.domain.SessionId
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * How long a hook may be ahead of the polled listing: while the supervisor still lists the session as
 * working, the state the hook reported holds for this long (AM-603 plan). After that polling wins.
 */
internal val HOOK_LEAD: Duration = 5.seconds

/** What a hook says the agent did; each one has the supervisor phase it leads to. */
internal enum class HookSignal { NEEDS_INPUT, PERMISSION, TURN_ENDED, FAILED }

/**
 * The latest hook of one conversation. [question] is user data (ENG-C8): `toString` hides it.
 *
 * @property at when Quayside received the hook.
 */
internal data class HookNote(
    val conversationId: ConversationId,
    val signal: HookSignal,
    val question: String?,
    val tool: String?,
    val at: Instant,
) {
    override fun toString(): String =
        "HookNote(conversationId=$conversationId, signal=$signal, question=${question?.let { "***" }}, " +
            "tool=$tool, at=$at)"
}

/** Hook data the reducer puts into a session (state details and `lastActivityAt`). */
internal data class HookDetail(
    val at: Instant,
    val question: String?,
    val tool: String?,
)

/** The note of an event, or null for `SessionEnded`: how a session ended is for polling to decide. */
internal fun AgentEvent.toNote(): HookNote? {
    val id = meta.conversationId
    val at = meta.at
    return when (this) {
        is AgentEvent.NeedsInput -> HookNote(id, HookSignal.NEEDS_INPUT, question, null, at)
        is AgentEvent.PermissionRequested -> HookNote(id, HookSignal.PERMISSION, null, tool, at)
        is AgentEvent.TurnEnded -> HookNote(id, HookSignal.TURN_ENDED, null, null, at)
        is AgentEvent.Failed -> HookNote(id, HookSignal.FAILED, null, null, at)
        is AgentEvent.SessionEnded -> null
    }
}

/** A listing with hooks laid over it, the details they add per session and the notes still in force. */
internal data class HookMerge(
    val sessions: List<RuntimeSession>,
    val details: Map<SessionId, HookDetail>,
    val notes: Map<ConversationId, HookNote>,
)

/**
 * Pure (ENG-C9): merges the latest hook of each conversation into a successful listing (ADR 0004,
 * polling stays the source of truth).
 *
 * For a listed session with a note:
 * - the listed phase is the one the hook leads to: the note stays and adds its details;
 * - the listing still says `WORKING` and the note is younger than the lead: the session is shown in
 *   the phase of the hook (the supervisor lags behind), the note stays;
 * - anything else: polling wins and the note is dropped. Notes of conversations that are not listed
 *   are dropped too.
 */
internal object HookOverlay {
    fun apply(
        listing: List<RuntimeSession>,
        notes: Map<ConversationId, HookNote>,
        now: Instant,
        lead: Duration,
    ): HookMerge {
        val kept = mutableMapOf<ConversationId, HookNote>()
        val details = mutableMapOf<SessionId, HookDetail>()
        val sessions =
            listing.map { session ->
                val note = session.conversationId?.let(notes::get)
                val leading = note != null && session.leadsBy(note, now, lead)
                when {
                    note == null -> {
                        session
                    }

                    session.phase == note.signal.phase() || leading -> {
                        kept[note.conversationId] = note
                        details[session.id] = HookDetail(note.at, note.question, note.tool)
                        session.copy(phase = note.signal.phase(), waitingFor = note.signal.waitingFor(session))
                    }

                    else -> {
                        session
                    }
                }
            }
        return HookMerge(sessions, details, kept)
    }

    private fun RuntimeSession.leadsBy(
        note: HookNote,
        now: Instant,
        lead: Duration,
    ): Boolean = phase == RuntimePhase.WORKING && now < note.at.plus(lead.toJavaDuration())

    private fun HookSignal.phase(): RuntimePhase =
        when (this) {
            HookSignal.NEEDS_INPUT, HookSignal.PERMISSION -> RuntimePhase.BLOCKED
            HookSignal.TURN_ENDED -> RuntimePhase.DONE
            HookSignal.FAILED -> RuntimePhase.FAILED
        }

    // A listed blocked session keeps its own reason: the supervisor knows it better than the hook.
    private fun HookSignal.waitingFor(session: RuntimeSession): WaitingFor? =
        when {
            session.phase == phase() -> session.waitingFor
            this == HookSignal.NEEDS_INPUT -> WaitingFor.Input
            this == HookSignal.PERMISSION -> WaitingFor.Permission
            else -> null
        }
}
