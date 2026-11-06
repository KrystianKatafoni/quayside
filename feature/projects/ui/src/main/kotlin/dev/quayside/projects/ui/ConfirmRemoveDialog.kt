package dev.quayside.projects.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import dev.quayside.sessions.api.SessionCounts

/**
 * Remove confirmation. The start focus is on "Anuluj" (also after a return from [Dialog.Waiting.BUSY]):
 * Space and Enter on a focused button count as a click, so no button that changes files may hold it (NFR-5).
 * In production [Dialog.Waiting.BUSY] usually renders, so the focus returns anyway; the effect below covers a
 * result that arrives before BUSY is drawn.
 */
@Composable
internal fun ConfirmRemove(
    dialog: Dialog.ConfirmRemove,
    onEvent: (SidebarEvent) -> Unit,
) {
    val focus = remember { FocusRequester() }
    // Keyed on the hooks result too: the clicked "Odinstaluj hooki" holds the focus and then leaves the dialog.
    LaunchedEffect(dialog.hooks, dialog.hooksNotice) { focus.requestFocus() }
    Panel(ProjectsTags.CONFIRM_REMOVE, ProjectsStrings["remove.title"]) {
        DialogText(dialog.project.displayName)
        HooksWarning(dialog, onEvent)
        RemoveChoices(dialog.active, onEvent)
        Cancel(focus) { onEvent(SidebarEvent.Dismiss) }
    }
}

/** The hooks left in the project file (FR-P3): warning and "Odinstaluj hooki" only when ours are there. */
@Composable
private fun HooksWarning(
    dialog: Dialog.ConfirmRemove,
    onEvent: (SidebarEvent) -> Unit,
) {
    when (dialog.hooks) {
        // Nothing to warn about.
        ProjectHooksState.ABSENT -> {}

        ProjectHooksState.PRESENT -> {
            DialogText(ProjectsStrings["remove.hooks.present"])
            DialogButton(ProjectsStrings["remove.hooks.uninstall"], ProjectsTags.UNINSTALL_HOOKS) {
                onEvent(SidebarEvent.UninstallHooks)
            }
        }

        ProjectHooksState.UNKNOWN -> {
            DialogText(ProjectsStrings["remove.hooks.unknown"])
        }
    }
    dialog.hooksNotice?.let { DialogText(ProjectsStrings.format(it)) }
}

/** The remove buttons of FR-P4; [active] null = the session counts are not known. */
@Composable
private fun RemoveChoices(
    active: SessionCounts?,
    onEvent: (SidebarEvent) -> Unit,
) {
    val keepSessions = { onEvent(SidebarEvent.RemoveKeepSessions) }
    when {
        active == null -> {
            DialogText(ProjectsStrings["remove.sessionsUnknown"])
            DialogButton(ProjectsStrings["remove.removeFromList"], ProjectsTags.REMOVE, onClick = keepSessions)
        }

        active.active == 0 -> {
            DialogButton(ProjectsStrings["remove.confirm"], ProjectsTags.REMOVE, onClick = keepSessions)
        }

        else -> {
            DialogText(ProjectsStrings.format(UiText("remove.activeSessions", listOf(active.active.toString()))))
            DialogButton(ProjectsStrings["remove.stopAndRemove"], ProjectsTags.STOP_AND_REMOVE) {
                onEvent(SidebarEvent.StopAndRemove)
            }
            DialogButton(
                ProjectsStrings["remove.keepSessions"],
                ProjectsTags.REMOVE_KEEP_SESSIONS,
                onClick = keepSessions,
            )
        }
    }
}
