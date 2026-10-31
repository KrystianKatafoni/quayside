package dev.agentmanager.app

import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.sessions.api.SessionState
import dev.agentmanager.sessions.api.SessionsSnapshot
import dev.agentmanager.terminal.ui.TabKey
import dev.agentmanager.terminal.ui.TabStatus
import dev.agentmanager.terminal.ui.TerminalController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** The key of the attach tab of [id], `attach:<id>`; `⏎` and `r` of one session share this tab (AM-510). */
internal fun attachTabKey(id: SessionId): TabKey = TabKey("attach:${id.value}")

/** The dot of a session in [state]: both waiting states show as one (AM-510). */
internal fun tabStatusOf(state: SessionState): TabStatus =
    when (state) {
        is SessionState.AwaitingInput, is SessionState.AwaitingPermission -> TabStatus.WAITING
        SessionState.Working -> TabStatus.WORKING
        SessionState.Done -> TabStatus.DONE
        SessionState.Failed -> TabStatus.FAILED
        SessionState.Stopped -> TabStatus.STOPPED
    }

/** The dots of all attach tabs of [snapshot], one per listed session. */
internal fun attachTabStatuses(snapshot: SessionsSnapshot): Map<TabKey, TabStatus> =
    snapshot.sessions.associate { attachTabKey(it.id) to tabStatusOf(it.state) }

/** Shows the dot of every attach tab for each snapshot of [snapshots], in [scope] (no `stateIn`, AM-510). */
internal fun TerminalController.followSessions(
    snapshots: Flow<SessionsSnapshot>,
    scope: CoroutineScope,
) {
    scope.launch { snapshots.collect { showStatuses(attachTabStatuses(it)) } }
}
