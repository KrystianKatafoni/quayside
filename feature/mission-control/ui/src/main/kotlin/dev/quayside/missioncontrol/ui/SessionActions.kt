package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.SessionId
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionOrigin
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot

/** Actions of the details panel and the row menu (AM-410); the place assignment (AM-314) is not in the palette. */
internal enum class SessionAction { STOP, RESPAWN, RESUME, ASSIGN_PLACE, REMOVE }

/** Why [action] on session [id] failed; [reason] is a fixed text without the id, the name or a path. */
internal data class Failure(
    val id: SessionId,
    val action: SessionAction,
    val reason: UiText,
)

/** The running action ([busy]: which session and action), the last [failure], the open [dialog] and the live session
 *  [copied] by the last resume (docs/compat.md R4), whose details show the copy notice. */
internal data class ActionUi(
    val busy: Pair<SessionId, SessionAction>? = null,
    val failure: Failure? = null,
    val dialog: SessionDialog? = null,
    val copied: SessionId? = null,
)

/** A dialog of Mission Control over the window (AM-410). */
internal sealed interface SessionDialog {
    /**
     * Asks to confirm removing session [id] from the list; [name] is shown only, never logged. [fromDisk]: a disk
     * session, which is hidden for good while its session file stays (FR-P9), and the hint says so.
     */
    data class ConfirmRemove(
        val id: SessionId,
        val name: String,
        val fromDisk: Boolean = false,
    ) : SessionDialog

    /**
     * The list of places for session [id] (FR-P9, AM-314): [choices] in order, [current] the id of the one that holds
     * now.
     */
    data class AssignPlace(
        val id: SessionId,
        val choices: List<PlaceChoice>,
        val current: String,
    ) : SessionDialog
}

/** Which actions apply to which session (plan, table 1). */
internal object SessionActions {
    /** The selected session when it is in the shown global view or project page ([SessionScope]), else null. */
    fun target(
        sessions: List<Session>,
        selection: Selection,
        projects: List<Project>,
    ): Session? = SessionScope.visible(sessions, selection, projects).find { it.id == selection.session }

    /**
     * Active sessions can be stopped, the others respawned or removed; a disk session ([SessionOrigin.DISK]) can be
     * resumed or removed (hidden, FR-P9); nothing while an action or a dialog is open. A session of a group
     * ([inGroup]: its project has subfolders) can also be assigned to a place, in any state and origin (AM-314).
     */
    fun available(
        state: SessionState,
        busy: Boolean,
        origin: SessionOrigin = SessionOrigin.SUPERVISOR,
        inGroup: Boolean = false,
    ): Set<SessionAction> {
        val own =
            when {
                busy -> emptySet()
                origin == SessionOrigin.DISK -> setOf(SessionAction.RESUME, SessionAction.REMOVE)
                state.isActive -> setOf(SessionAction.STOP)
                else -> setOf(SessionAction.RESPAWN, SessionAction.REMOVE)
            }
        return if (inGroup && !busy) own + SessionAction.ASSIGN_PLACE else own
    }

    /** [available] for [session], with `inGroup` read from the project of the session in [projects]. */
    fun availableFor(
        session: Session,
        projects: List<Project>,
        busy: Boolean,
    ): Set<SessionAction> {
        val inGroup = projects.any { it.id == session.project && it.subfolders.isNotEmpty() }
        return available(session.state, busy, session.origin, inGroup)
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
    val target = SessionActions.target(snapshot.sessions, selection, projects)
    if (details == null || target == null) return withDialog
    val attach = SessionAttach.targetOf(target, projects, acting.dialog != null)
    val shown =
        details.copy(
            canAttach = attach != null,
            canReply = attach?.waiting == true,
            actions =
                SessionActions.availableFor(target, projects, busy = acting.busy != null || acting.dialog != null),
            busy = acting.busy?.takeIf { it.first == details.id }?.second,
            failure = acting.failure?.takeIf { it.id == details.id },
            notice = UiText("action.resume.copyNotice").takeIf { acting.copied == details.id },
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
