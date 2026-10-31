package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.RecentOutput
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionMonitor
import dev.quayside.sessions.api.SessionsApi
import kotlinx.coroutines.CompletableDeferred

/** [FakeSessions] as both sides of the sessions feature. */
internal fun FakeSessions.api(): SessionsApi = SessionsApi(monitor = this, commands = this)

/**
 * Commands that stay "in progress" until the test opens [gate]: stop, respawn and remove record the call in [started],
 * wait for the gate and then delegate to [inner]. Gives the "action runs" state without a real clock.
 */
internal class GatedCommands(
    private val inner: SessionCommands,
) : SessionCommands by inner {
    val gate = CompletableDeferred<Unit>()
    val started = mutableListOf<String>()

    override suspend fun stop(id: SessionId): Outcome<Unit, SessionError> {
        started += "stop ${id.value}"
        gate.await()
        return inner.stop(id)
    }

    override suspend fun respawn(id: SessionId): Outcome<Unit, SessionError> {
        started += "respawn ${id.value}"
        gate.await()
        return inner.respawn(id)
    }

    override suspend fun remove(id: SessionId): Outcome<Unit, SessionError> {
        started += "remove ${id.value}"
        gate.await()
        return inner.remove(id)
    }
}

/**
 * A monitor whose [recentOutput] waits while the test [hold]s it: every read records its id in [started], waits for
 * the current gate and then delegates to [inner]. Open by default; [release] lets the waiting reads continue.
 * Gives the "slow read" state without a real clock.
 */
internal class GatedOutput(
    private val inner: SessionMonitor,
) : SessionMonitor by inner {
    private var gate = CompletableDeferred<Unit>().also { it.complete(Unit) }
    val started = mutableListOf<SessionId>()

    /** Reads that start from now on wait until [release]. */
    fun hold() {
        gate = CompletableDeferred()
    }

    fun release() {
        gate.complete(Unit)
    }

    override suspend fun recentOutput(id: SessionId): Outcome<RecentOutput, SessionError> {
        started += id
        gate.await()
        return inner.recentOutput(id)
    }
}
