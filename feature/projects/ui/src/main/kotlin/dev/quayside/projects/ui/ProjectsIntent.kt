package dev.quayside.projects.ui

import dev.quayside.core.domain.ProjectId

/**
 * Which window-menu commands are enabled. All false while a dialog is open, so ⌘⌫ reaches text fields.
 * @property dialogOpen a dialog of the sidebar is open; `app` then offers no project commands in the palette.
 */
public data class ProjectsMenuState(
    val canAdd: Boolean,
    val canRemove: Boolean,
    val canRename: Boolean,
    val dialogOpen: Boolean,
)

/** User intents from the sidebar and the window menu (MVI, docs/11 §1). */
public sealed interface ProjectsIntent {
    /** ⌘⇧O or the sidebar button: pick a folder, inspect it, confirm adding. */
    public data object AddRequested : ProjectsIntent

    /** ⌘⌫: remove the selected project after a confirmation; ignored without a selection. */
    public data object RemoveSelectedRequested : ProjectsIntent

    /** "Rename project…" in the window menu or the palette: opens the rename dialog; ignored without a selection. */
    public data object RenameSelectedRequested : ProjectsIntent

    /** "Go to project" in the palette: selects [id]; ignored for a project that is not listed. */
    public data class Select(
        val id: ProjectId,
    ) : ProjectsIntent

    /** A global view of Mission Control is shown: no project is selected; ignored while a dialog is open. */
    public data object ClearSelection : ProjectsIntent
}
