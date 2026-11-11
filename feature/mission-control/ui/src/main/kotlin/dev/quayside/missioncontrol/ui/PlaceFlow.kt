package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.SessionId
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.PlaceAssignment
import dev.quayside.sessions.api.Session
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The window "Przypisz do podfolderu…" (FR-P9, AM-314) over the action state [ui] of the controller. [target] reads the
 * selected visible session from the snapshot now, [projects] the project list; [assign] runs the command.
 */
internal class PlaceFlow(
    private val ui: MutableStateFlow<ActionUi>,
    private val target: () -> Session?,
    private val projects: () -> List<Project>,
    private val assign: (SessionId, PlaceAssignment?) -> Unit,
) {
    /** Opens the list for the selected session; nothing is called until a line is picked. */
    fun open() {
        val session = target()
        if (ui.value.busy != null || ui.value.dialog != null || session == null) return
        dialogFor(session, projects())?.let { ui.value = ActionUi(dialog = it) }
    }

    /**
     * Assigns the line [line] of the open list, read from the dialog and not from the selection; the current line
     * only closes the list, and a line the list does not have changes nothing.
     */
    fun pick(line: String) {
        val dialog = ui.value.dialog as? SessionDialog.AssignPlace
        val picked = dialog?.choices?.firstOrNull { it.id == line } ?: return
        if (picked.id == dialog.current) ui.value = ActionUi() else assign(dialog.id, picked.assignment)
    }

    /**
     * The list for [session], or null when the action is not offered for it (no project of its own, a project without
     * subfolders). The current choice is read from the session as it is now.
     */
    private fun dialogFor(
        session: Session,
        projects: List<Project>,
    ): SessionDialog.AssignPlace? {
        val project = projects.firstOrNull { it.id == session.project }
        val offered = SessionAction.ASSIGN_PLACE in SessionActions.availableFor(session, projects, busy = false)
        return if (project == null || !offered) {
            null
        } else {
            SessionDialog.AssignPlace(session.id, PlaceChoices.of(project), PlaceChoices.current(project, session))
        }
    }
}
