package dev.quayside.projects.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.DirectoryReport
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.SubfolderName
import dev.quayside.sessions.api.SessionCounts

/**
 * What the sidebar shows.
 *
 * @property projects registered projects in list order.
 * @property selected selected project, null = none (the initial state).
 * @property banner fixed text of the start-up storage issue until the user dismisses it.
 * @property dialog the one open dialog, null = none.
 * @property sessions per-project counts of active sessions from the sessions snapshot.
 * @property subfolder selected subfolder of [selected], null = the project row itself (FR-P7).
 * @property collapsed groups whose subfolder rows are hidden; in memory only.
 */
internal data class ProjectsUiState(
    val projects: List<Project> = emptyList(),
    val selected: ProjectId? = null,
    val banner: UiText? = null,
    val dialog: Dialog? = null,
    val sessions: SessionsView = SessionsView.Unknown,
    val subfolder: SubfolderName? = null,
    val collapsed: Set<ProjectId> = emptySet(),
)

/** The dialog flow in progress; at most one at a time. */
internal sealed interface Dialog {
    /** The system folder chooser is open; it is modal, so the sidebar shows nothing over it. */
    data object Picking : Dialog

    /** A step is running; [INSPECTING] ends with [SidebarEvent.Dismiss], [BUSY] does not. */
    enum class Waiting(
        val tag: String,
        val key: String,
    ) : Dialog {
        /** The picked folder is being inspected. */
        INSPECTING(ProjectsTags.INSPECTING, "add.inspecting"),

        /** A confirmed command (add, stop, remove, rename) is running. */
        BUSY(ProjectsTags.BUSY, "dialog.busy"),

        /** The hooks of the project are being read before the remove dialog opens; Dismiss cancels it. */
        CHECKING_HOOKS(ProjectsTags.CHECKING_HOOKS, "remove.checkingHooks"),

        /** The subdirectories of the project are being listed before the subfolders dialog opens; Dismiss cancels. */
        LISTING(ProjectsTags.LISTING, "subfolders.listing"),
    }

    /** Inspection report waiting for "Add"; [error] is set when adding failed and the dialog stays. */
    data class ConfirmAdd(
        val report: DirectoryReport,
        val error: UiText? = null,
    ) : Dialog

    /** Remove confirmation; [active] = counts frozen when the dialog opened, null = the snapshot was not fresh. */
    data class ConfirmRemove(
        val project: Project,
        val active: SessionCounts?,
        val hooks: ProjectHooksState = ProjectHooksState.ABSENT,
        val hooksNotice: UiText? = null,
    ) : Dialog

    /** New name of [project] typed so far in [draft]; [error] is set when saving failed and the dialog stays. */
    data class Rename(
        val project: Project,
        val draft: String,
        val error: UiText? = null,
    ) : Dialog

    /**
     * The subfolders dialog of [project] (FR-P7): every [choices] entry with its state, [checked] = what "Zapisz"
     * would store; [error] is set when saving failed and the dialog stays.
     */
    data class Subfolders(
        val project: Project,
        val choices: List<SubfolderChoice>,
        val checked: Set<SubfolderName>,
        val error: UiText? = null,
    ) : Dialog

    /** A fixed message without paths, closed with [SidebarEvent.Dismiss]. */
    data class Message(
        val text: UiText,
    ) : Dialog
}

/** One line of the subfolders dialog: a [name] from the disk (or from the project, when gone) and its [state]. */
internal data class SubfolderChoice(
    val name: SubfolderName,
    val state: ChoiceState,
)

/** Whether a line of the subfolders dialog can be checked. */
internal enum class ChoiceState {
    /** A real directory that can be checked. */
    AVAILABLE,

    /** The directory is a registered project of its own; shown disabled, "jest osobnym projektem". */
    OWN_PROJECT,

    /** The project lists it but the disk does not; shown checked, "brak na dysku", can be unchecked. */
    MISSING,
}

/** Clicks inside the sidebar and its dialogs. */
internal sealed interface SidebarEvent {
    /** A row click (a subfolder row has [subfolder]); ignored while a dialog is open. */
    data class Select(
        val id: ProjectId,
        val subfolder: SubfolderName? = null,
    ) : SidebarEvent

    /** The chevron of the group [id]: hides or shows its subfolder rows; ignored for a project without subfolders. */
    data class ToggleGroup(
        val id: ProjectId,
    ) : SidebarEvent

    /** "Podfoldery…" in the row menu of project [id]; ignored while a dialog is open. */
    data class EditSubfoldersRequested(
        val id: ProjectId,
    ) : SidebarEvent

    /** A click on the line [name] of [Dialog.Subfolders]; ignored for a line that is [ChoiceState.OWN_PROJECT]. */
    data class ToggleSubfolder(
        val name: SubfolderName,
    ) : SidebarEvent

    /** "Zapisz" in [Dialog.Subfolders]. */
    data object SaveSubfolders : SidebarEvent

    /** "Rename…" in the row menu of project [id]; ignored while a dialog is open. */
    data class RenameRequested(
        val id: ProjectId,
    ) : SidebarEvent

    /** The user typed [text] into the field of [Dialog.Rename]. */
    data class NameChanged(
        val text: String,
    ) : SidebarEvent

    /** "Save" or Enter in [Dialog.Rename]. */
    data object ConfirmRename : SidebarEvent

    /** "Add" in [Dialog.ConfirmAdd]. */
    data object ConfirmAdd : SidebarEvent

    /** "Stop sessions and remove" in [Dialog.ConfirmRemove] with active sessions. */
    data object StopAndRemove : SidebarEvent

    /** "Remove" / "Remove from list, sessions keep running" in [Dialog.ConfirmRemove]. */
    data object RemoveKeepSessions : SidebarEvent

    /** "Cancel" or "Close": closes the open dialog and cancels a running inspection. */
    data object Dismiss : SidebarEvent

    /** "Odinstaluj hooki" in [Dialog.ConfirmRemove] with hooks present. */
    data object UninstallHooks : SidebarEvent

    /** Closes the storage issue banner. */
    data object DismissBanner : SidebarEvent
}
