package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.MessageCursor
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionMessagePage
import dev.quayside.sessions.api.SessionMonitor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * A monitor whose [conversation] waits while the test holds it: a read for an older page ([holdOlder], `before` set),
 * every read ([holdAll]) or the first page of one session ([holdFirstOf]) records itself in [started], waits for
 * [release] and then delegates to [inner]. The wait ignores cancellation, as a read that is already running in the
 * file does: its late result must be dropped by the reader of it, not by the cancel. Gives the "slow read" state
 * without a real clock.
 */
internal class GatedConversation(
    private val inner: SessionMonitor,
) : SessionMonitor by inner {
    private var gate = CompletableDeferred<Unit>().also { it.complete(Unit) }
    private var older = false
    private var only: SessionId? = null
    val started = mutableListOf<Pair<SessionId, MessageCursor?>>()

    /** Reads with a cursor start waiting from now on; the first page of a session is not held. */
    fun holdOlder() {
        gate = CompletableDeferred()
        older = true
        only = null
    }

    /** Every read starts waiting from now on. */
    fun holdAll() {
        gate = CompletableDeferred()
        older = false
        only = null
    }

    /** Only the first page of [id] waits from now on; reads of other sessions and older pages go through. */
    fun holdFirstOf(id: SessionId) {
        gate = CompletableDeferred()
        older = false
        only = id
    }

    fun release() {
        gate.complete(Unit)
    }

    override suspend fun conversation(
        id: SessionId,
        before: MessageCursor?,
    ): Outcome<SessionMessagePage, SessionError> {
        started += id to before
        val held = if (only != null) before == null && id == only else before != null || !older
        if (held) withContext(NonCancellable) { gate.await() }
        return inner.conversation(id, before)
    }
}
