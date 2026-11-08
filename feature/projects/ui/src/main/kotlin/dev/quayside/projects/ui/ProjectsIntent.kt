package dev.quayside.projects.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.SubfolderName

/**
 * Which window-menu commands are enabled. All false while a dialog is open, so ⌘⌫ reaches text fields.
 * @property dialogOpen a dialog of the sidebar is open; `app` then offers no project commands in the palette.
 * @property canEditSubfolders a project is selected and no dialog is open: "Podfoldery projektu…" is offered.
 */
public data class ProjectsMenuState(
    val canAdd: Boolean,
    val canRemove: Boolean,
    val canRename: Boolean,
    val dialogOpen: Boolean,
    val canEditSubfolders: Boolean = false,
)

/** User intents from the sidebar and the window menu (MVI, docs/11 §1). */
public sealed interface ProjectsIntent {
    /** ⌘⇧O or the sidebar button: pick a folder, inspect it, confirm adding. */
    public data object AddRequested : ProjectsIntent

    /** ⌘⌫: remove the selected project after a confirmation; ignored without a selection. */
    public data object RemoveSelectedRequested : ProjectsIntent

    /** "Rename project…" in the window menu or the palette: opens the rename dialog; ignored without a selection. */
    public data object RenameSelectedRequested : ProjectsIntent

    /**
     * "Go to project" in the palette: selects [id], and [subfolder] when given; selecting [id] alone clears the
     * subfolder. Ignored for a project that is not listed or a subfolder that is not in its group.
     */
    public data class Select(
        val id: ProjectId,
        val subfolder: SubfolderName? = null,
    ) : ProjectsIntent

    /** "Podfoldery projektu…" in the palette: opens the subfolders dialog of the selected project. */
    public data object EditSubfoldersRequested : ProjectsIntent

    /** A global view of Mission Control is shown: no project is selected; ignored while a dialog is open. */
    public data object ClearSelection : ProjectsIntent
}
