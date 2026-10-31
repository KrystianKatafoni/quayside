package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.map
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionsSnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Single owner of the listing and the published state. [refresh] lists the supervisor, lays the pending
 * command effects over the listing ([CommandOverlay]), reduces against the current snapshot, sets
 * [snapshot] and emits the events, all under one Mutex: no older listing ever overwrites a newer one.
 * The polling loop (AM-403) and the commands (AM-404) publish through the same instance.
 *
 * Events are emitted under the mutex, after [snapshot] is set. With a full buffer (64 events) a
 * collector that stops collecting therefore also holds back every later snapshot update and command;
 * consumers (AM-405) must not collect [events] on a thread that may block or stall.
 */
internal class SessionsState(
    private val runtime: AgentRuntime,
    private val projects: ProjectsApi,
    private val clock: Clock,
    private val overlayTtl: Duration,
) {
    private val mutex = Mutex()
    private val current = MutableStateFlow(SessionsSnapshot.Initial)
    private val emitted = MutableSharedFlow<SessionEvent>(extraBufferCapacity = EVENT_BUFFER)

    /** Expectations of successful commands not shown by the supervisor yet; guarded by [mutex]. */
    private var pending: List<Expected> = emptyList()

    /** Latest snapshot, [SessionsSnapshot.Initial] until the first [refresh]. */
    val snapshot: StateFlow<SessionsSnapshot> = current.asStateFlow()

    /**
     * Transitions without replay; each one is emitted after [snapshot] shows it, under the refresh mutex.
     * A stalled collector with a full buffer stalls [refresh] and so the snapshot too.
     */
    val events: SharedFlow<SessionEvent> = emitted.asSharedFlow()

    /**
     * Adds [expect], then lists, overlays, reduces and publishes under one Mutex. Returns the published
     * snapshot, or the listing's error (the snapshot is then stale and the expectations are kept).
     */
    suspend fun refresh(expect: List<Expected> = emptyList()): Outcome<SessionsSnapshot, RuntimeError> =
        mutex.withLock {
            pending = pending + expect
            val listing = runtime.listSessions(under = null)
            val now = clock.now()
            val dirs = projects.projects.value.associate { it.id to it.dir }
            val reduction = SessionStateReducer.reduce(current.value, listing.map { overlay(it, now) }, dirs, now)
            current.value = reduction.snapshot
            reduction.events.forEach { emitted.emit(it) }
            listing.map { reduction.snapshot }
        }

    /** Lays [pending] over a successful [listing] and keeps the expectations still pending; under [mutex]. */
    private fun overlay(
        listing: List<RuntimeSession>,
        now: Instant,
    ): List<RuntimeSession> {
        val result = CommandOverlay.apply(listing, pending, now)
        pending = result.pending
        return result.sessions
    }

    /** Until when a command started now lays its effect over listings: the clock's time plus the overlay TTL. */
    fun expiry(): Instant = clock.now().plus(overlayTtl.toJavaDuration())

    private companion object {
        // docs/11 §6: a slow collector suspends publishing instead of losing events.
        const val EVENT_BUFFER = 64
    }
}
