package dev.quayside.projects.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.SubfolderName

/** Semantics tags of the sidebar for UI tests. */
internal object ProjectsTags {
    const val SIDEBAR = "projects.sidebar"
    const val EMPTY = "projects.empty"
    const val ADD_BUTTON = "projects.add"
    const val ADD_FROM_DISK = "projects.addFromDisk"
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
    const val CHECKING_HOOKS = "projects.dialog.checking-hooks"

    /** "Odinstaluj hooki" in the remove dialog with our hooks in the project file (AM-610). */
    const val UNINSTALL_HOOKS = "projects.dialog.remove.hooks.uninstall"

    /** The only remove button: no active sessions, or they could not be listed. */
    const val REMOVE = "projects.dialog.remove.confirm"
    const val STOP_AND_REMOVE = "projects.dialog.remove.stop"
    const val REMOVE_KEEP_SESSIONS = "projects.dialog.remove.keep"
    const val MESSAGE = "projects.dialog.message"

    /** The question "Znaleziono N starych sesji" after adding a folder and its answers (FR-P9). */
    const val PAST_DIALOG = "projects.dialog.past"
    const val PAST_SHOW = "projects.dialog.past.show"
    const val PAST_LATER = "projects.dialog.past.later"
    const val CANCEL = "projects.dialog.cancel"

    /** "Podfoldery…" in the row menu, the dialog, its "Zapisz" and the waiting state before it opens (AM-306). */
    const val ROW_MENU_SUBFOLDERS = "projects.row.menu.subfolders"
    const val SUBFOLDERS_DIALOG = "projects.dialog.subfolders"
    const val SUBFOLDERS_SAVE = "projects.dialog.subfolders.save"
    const val LISTING = "projects.dialog.listing"

    const val RAIL = "projects.rail"
    const val RAIL_ADD = "projects.rail.add"

    /** The choice of the age limit [age] and of the count limit [limit] (enum names) in the add dialog (FR-P9). */
    fun historyAge(age: String): String = "projects.dialog.add.historyAge.$age"

    fun historyLimit(limit: String): String = "projects.dialog.add.historyLimit.$limit"

    /** The rail button of project [id]. */
    fun railButton(id: ProjectId): String = "projects.rail.${id.value}"

    /** The row of project [id]; clicking it selects the project. */
    fun row(id: ProjectId): String = "projects.row.${id.value}"

    /** The chevron of the group row of project [id]. */
    fun chevron(id: ProjectId): String = "projects.row.${id.value}.chevron"

    /** The row of subfolder [name] of project [id]; clicking it selects the subfolder. */
    fun subfolderRow(
        id: ProjectId,
        name: SubfolderName,
    ): String = "projects.row.${id.value}.sub.${name.value}"

    /** The line of [name] in the subfolders dialog; a checkable, selectable node. */
    fun subfolderCheck(name: SubfolderName): String = "projects.dialog.subfolders.check.${name.value}"
}
