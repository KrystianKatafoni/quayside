package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.core.domain.SessionId
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How long the effect of a command is laid over listings that do not show it yet (AM-404 plan). */
internal val COMMAND_OVERLAY_TTL: Duration = 10.seconds

/** Effect of a successful command the supervisor may not list yet; it applies while the time is before [until]. */
internal sealed class Expected {
    abstract val id: SessionId
    abstract val until: Instant

    /** `start`: [session] is listed. */
    internal data class Appears(
        val session: RuntimeSession,
        override val until: Instant,
    ) : Expected() {
        override val id: SessionId get() = session.id
    }

    /** `stop`, `respawn`: session [id] moves [from] one phase [to] another. */
    internal data class Moves(
        override val id: SessionId,
        val from: RuntimePhase,
        val to: RuntimePhase,
        override val until: Instant,
    ) : Expected()

    /** `remove`: session [id] is no longer listed. */
    internal data class Disappears(
        override val id: SessionId,
        override val until: Instant,
    ) : Expected()
}

/** A listing with the pending expectations laid over it and the expectations still pending. */
internal data class Overlaid(
    val sessions: List<RuntimeSession>,
    val pending: List<Expected>,
)

/**
 * Pure (ENG-C9): lays pending expectations over a successful listing; drops confirmed, contradicted and
 * expired ones. Of several expectations for one session the last one wins, except that a move of a session
 * not listed yet keeps it appearing in the new phase; [Overlaid.pending] keeps the order of the input's ids.
 */
internal object CommandOverlay {
    fun apply(
        listing: List<RuntimeSession>,
        pending: List<Expected>,
        now: Instant,
    ): Overlaid {
        val latest =
            pending.filter { now < it.until }.fold(mapOf<SessionId, Expected>()) { acc, expected ->
                acc + (expected.id to expected.after(acc[expected.id]))
            }
        val listed = listing.map { it.id }.toSet()
        val kept = latest.values.filter { it.holdsOver(listing.firstOrNull { session -> session.id == it.id }) }
        val byId = kept.associateBy { it.id }
        val sessions =
            listing.mapNotNull { it.overlaid(byId[it.id]) } +
                kept.filterIsInstance<Expected.Appears>().filterNot { it.id in listed }.map { it.session }
        return Overlaid(sessions, kept)
    }

    /** [this] newer expectation over an [older] one for the same session. */
    private fun Expected.after(older: Expected?): Expected =
        if (this is Expected.Moves && older is Expected.Appears) {
            Expected.Appears(older.session.copy(phase = to, waitingFor = null), until)
        } else {
            this
        }

    /** True while [listed] (null = not listed) does not show the expectation yet and does not contradict it. */
    private fun Expected.holdsOver(listed: RuntimeSession?): Boolean =
        when (this) {
            is Expected.Appears -> listed == null
            is Expected.Moves -> listed?.phase == from
            is Expected.Disappears -> listed != null
        }

    /** [this] listed session as [expected] shows it (as listed without one); null when it is hidden. */
    private fun RuntimeSession.overlaid(expected: Expected?): RuntimeSession? =
        when (expected) {
            null, is Expected.Appears -> this
            is Expected.Moves -> copy(phase = expected.to, waitingFor = null)
            is Expected.Disappears -> null
        }
}
