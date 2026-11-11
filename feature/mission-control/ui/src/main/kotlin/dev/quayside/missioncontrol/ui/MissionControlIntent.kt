package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.projects.api.SubfolderName

/** Intents of Mission Control from the palette and window keys (MVI, docs/11 §1; ADR 0011). */
public sealed interface MissionControlIntent {
    /** `j`: selects the next visible session; without a selection the first; stays on the last (no wrap). */
    public data object NextSession : MissionControlIntent

    /** `k`: selects the previous visible session; without a selection the last; stays on the first. */
    public data object PreviousSession : MissionControlIntent

    /** Palette "Filter: …": shows only sessions that [filter] matches (FR-S9); ignored while a global view is shown. */
    public data class SelectFilter(
        val filter: SessionFilter,
    ) : MissionControlIntent

    /** `x` and the palette: stops the selected visible session when it is active; otherwise ignored (FR-S4). */
    public data object StopSelected : MissionControlIntent

    /** Palette and panel: respawns the selected visible session when it is not active; otherwise ignored. */
    public data object RespawnSelected : MissionControlIntent

    /**
     * Palette and panel: resumes the selected visible disk session (FR-P9) and, when it succeeds, selects the live
     * session on the filter "Wszystkie"; ignored for any other session.
     */
    public data object ResumeSelected : MissionControlIntent

    /** Palette and panel: asks to confirm removing the selected inactive session from the list; otherwise ignored. */
    public data object RemoveSelected : MissionControlIntent

    /** Sidebar entry and palette: shows [view] and clears the selected session; ignored while a dialog is open. */
    public data class ShowView(
        val view: GlobalView,
    ) : MissionControlIntent

    /**
     * `app`: selects [id] of [project] on the project page with filter ALL, e.g. a session just started (AM-608);
     * with [subfolder] on the page of that subfolder of the group (AM-308).
     */
    public data class SelectSession(
        val project: ProjectId,
        val id: SessionId,
        val subfolder: SubfolderName? = null,
    ) : MissionControlIntent

    /** `space` and the palette: opens the output preview of the selected visible session, or closes it. */
    public data object TogglePreview : MissionControlIntent
}

/**
 * Which session actions apply to the selected visible session now; all false without one, while an action
 * runs and while a dialog of Mission Control is open. `app` offers palette commands by it; the controller
 * checks again when it runs one.
 *
 * @property canStop the session is active.
 * @property canRespawn a supervisor session that is not active.
 * @property canRemove the session is not active (or from disk): it can be removed from the list after a confirmation.
 * @property canResume the selected session is a disk session (FR-P9) and nothing runs.
 * @property dialogOpen a dialog of Mission Control is open: `app` offers no palette commands and disables the menu.
 * @property canPreview a visible session is selected and no dialog is open; also while an action runs.
 * @property previewOpen the preview is open: the palette offers "Zamknij podgląd".
 * @property canAttach a visible session of a registered project is selected and no dialog is open; also while an
 *   action runs.
 * @property canReply [canAttach] and the session waits for the user (`r`, ADR 0013); also while an action runs.
 */
public data class SessionActionsState(
    val canStop: Boolean,
    val canRespawn: Boolean,
    val canRemove: Boolean = false,
    val dialogOpen: Boolean = false,
    val canPreview: Boolean = false,
    val previewOpen: Boolean = false,
    val canAttach: Boolean = false,
    val canReply: Boolean = false,
    val canResume: Boolean = false,
)
