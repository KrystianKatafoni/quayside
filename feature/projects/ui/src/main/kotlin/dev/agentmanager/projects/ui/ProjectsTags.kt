package dev.agentmanager.projects.ui

import dev.agentmanager.core.domain.ProjectId

/** Semantics tags of the sidebar for UI tests. */
internal object ProjectsTags {
    const val SIDEBAR = "projects.sidebar"
    const val EMPTY = "projects.empty"
    const val ADD_BUTTON = "projects.add"
    const val BANNER = "projects.banner"
    const val BANNER_DISMISS = "projects.banner.dismiss"
    const val ROW_MENU_REMOVE = "projects.row.menu.remove"
    const val ROW_MENU_RENAME = "projects.row.menu.rename"
    const val RENAME_DIALOG = "projects.dialog.rename"
    const val RENAME_FIELD = "projects.dialog.rename.field"
    const val RENAME_SAVE = "projects.dialog.rename.save"
    const val INSPECTING = "projects.dialog.inspecting"
    const val CONFIRM_ADD = "projects.dialog.add"
    const val ADD = "projects.dialog.add.confirm"
    const val CONFIRM_REMOVE = "projects.dialog.remove"
    const val BUSY = "projects.dialog.busy"

    /** The only remove button: no active sessions, or they could not be listed. */
    const val REMOVE = "projects.dialog.remove.confirm"
    const val STOP_AND_REMOVE = "projects.dialog.remove.stop"
    const val REMOVE_KEEP_SESSIONS = "projects.dialog.remove.keep"
    const val MESSAGE = "projects.dialog.message"
    const val CANCEL = "projects.dialog.cancel"

    /** The row of project [id]; clicking it selects the project. */
    fun row(id: ProjectId): String = "projects.row.${id.value}"

    /** The ⋯ button of the row of project [id]; opens the row menu. */
    fun rowMenu(id: ProjectId): String = "projects.row.${id.value}.menu"
}
