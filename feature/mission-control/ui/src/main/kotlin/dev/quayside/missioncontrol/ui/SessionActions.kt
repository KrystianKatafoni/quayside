package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.SessionId
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot

/** Actions of the details panel and the palette (AM-410). */
internal enum class SessionAction { STOP, RESPAWN, REMOVE }

/** Why [action] on session [id] failed; [reason] is a fixed text without the id, the name or a path. */
internal data class Failure(
    val id: SessionId,
    val action: SessionAction,
    val reason: UiText,
)

/** The running action ([busy]: which session and action), the last [failure] and the open [dialog]. */
internal data class ActionUi(
    val busy: Pair<SessionId, SessionAction>? = null,
    val failure: Failure? = null,
    val dialog: SessionDialog? = null,
)

/** A dialog of Mission Control over the window (AM-410). */
internal sealed interface SessionDialog {
    /** Asks to confirm removing session [id] from the list; [name] is shown only, never logged. */
    data class ConfirmRemove(
        val id: SessionId,
        val name: String,
    ) : SessionDialog
}

/** Which actions apply to which session (plan, table 1). */
internal object SessionActions {
    /** The selected session when it is in the shown global view or project page ([SessionScope]), else null. */
    fun target(
        sessions: List<Session>,
        selection: Selection,
    ): Session? = SessionScope.visible(sessions, selection).find { it.id == selection.session }

    /** Active sessions can be stopped, the others respawned or removed; nothing while an action or a dialog is open. */
    fun available(
        state: SessionState,
        busy: Boolean,
    ): Set<SessionAction> =
        when {
            busy -> emptySet()
            state.isActive -> setOf(SessionAction.STOP)
            else -> setOf(SessionAction.RESPAWN, SessionAction.REMOVE)
        }
}

/** The details of the selected session with the actions that apply now, the running one and its failure. */
internal fun MissionControlUiState.withActions(
    snapshot: SessionsSnapshot,
    selection: Selection,
    acting: ActionUi,
    projects: List<Project>,
): MissionControlUiState {
    val withDialog = copy(dialog = acting.dialog)
    val details = screen.details()
    val target = SessionActions.target(snapshot.sessions, selection)
    if (details == null || target == null) return withDialog
    val attach = SessionAttach.targetOf(target, projects, acting.dialog != null)
    val shown =
        details.copy(
            canAttach = attach != null,
            canReply = attach?.waiting == true,
            actions = SessionActions.available(target.state, busy = acting.busy != null || acting.dialog != null),
            busy = acting.busy?.takeIf { it.first == details.id }?.second,
            failure = acting.failure?.takeIf { it.id == details.id },
        )
    return withDialog.copy(screen = screen.withDetails(shown))
}

private fun Screen.details(): SessionDetails? =
    when (this) {
        is Screen.Project -> details
        is Screen.Global -> details
        else -> null
    }

private fun Screen.withDetails(shown: SessionDetails): Screen =
    when (this) {
        is Screen.Project -> copy(details = shown)
        is Screen.Global -> copy(details = shown)
        else -> this
    }
