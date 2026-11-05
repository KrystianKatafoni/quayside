package dev.quayside.app

import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.terminal.ui.TabKey
import dev.quayside.terminal.ui.TabStatus
import dev.quayside.terminal.ui.TerminalController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** The key of the attach tab of [id], `attach:<id>`; `⏎` and `r` of one session share this tab (AM-510). */
internal fun attachTabKey(id: SessionId): TabKey = TabKey("$ATTACH_PREFIX${id.value}")

/** The session of an attach tab key (`attach:<id>`); null for any other key (plan AM-809, session bar). */
internal fun sessionOfTabKey(key: TabKey): SessionId? =
    key.value
        .removePrefix(ATTACH_PREFIX)
        .takeIf {
            key.value.startsWith(ATTACH_PREFIX) && it.isNotEmpty()
        }?.let(::SessionId)

private const val ATTACH_PREFIX = "attach:"

/** The status of a session in [state]: both waiting states show as one (AM-510). */
internal fun tabStatusOf(state: SessionState): TabStatus =
    when (state) {
        is SessionState.AwaitingInput, is SessionState.AwaitingPermission -> TabStatus.WAITING
        SessionState.Working -> TabStatus.WORKING
        SessionState.Done -> TabStatus.DONE
        SessionState.Failed -> TabStatus.FAILED
        SessionState.Stopped -> TabStatus.STOPPED
    }

/** The status icons of all attach tabs of [snapshot], one per listed session. */
internal fun attachTabStatuses(snapshot: SessionsSnapshot): Map<TabKey, TabStatus> =
    snapshot.sessions.associate { attachTabKey(it.id) to tabStatusOf(it.state) }

/** Shows the status icon of every attach tab for each snapshot of [snapshots], in [scope] (no `stateIn`, AM-510). */
internal fun TerminalController.followSessions(
    snapshots: Flow<SessionsSnapshot>,
    scope: CoroutineScope,
) {
    scope.launch { snapshots.collect { showStatuses(attachTabStatuses(it)) } }
}
