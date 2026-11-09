package dev.quayside.projects.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.toColor

/**
 * The subfolders dialog (FR-P7): one checkable line per directory. The start focus is on "Anuluj", also after a
 * failed save: Space and Enter on a focused "Zapisz" would change the stored list (NFR-5).
 */
@Composable
internal fun SubfoldersDialog(
    dialog: Dialog.Subfolders,
    onEvent: (SidebarEvent) -> Unit,
) {
    val focus = remember { FocusRequester() }
    // Also after a failed save: saving shows `Dialog.Waiting.BUSY`, so the dialog is composed anew and this runs again.
    LaunchedEffect(Unit) { focus.requestFocus() }
    Panel(ProjectsTags.SUBFOLDERS_DIALOG, ProjectsStrings["subfolders.title"]) {
        DialogText(dialog.project.displayName, muted = true)
        if (dialog.choices.isEmpty()) DialogText(ProjectsStrings["subfolders.empty"])
        Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            dialog.choices.forEach { choice ->
                ChoiceLine(choice, choice.name in dialog.checked) { onEvent(SidebarEvent.ToggleSubfolder(choice.name)) }
            }
        }
        dialog.error?.let { DialogText(ProjectsStrings.format(it)) }
        DialogButton(ProjectsStrings["subfolders.save"], ProjectsTags.SUBFOLDERS_SAVE) {
            onEvent(SidebarEvent.SaveSubfolders)
        }
        Cancel(focus) { onEvent(SidebarEvent.Dismiss) }
    }
}

/** A checkbox line; a project of its own is disabled and says so, a vanished directory is checked and says so. */
@Composable
private fun ChoiceLine(
    choice: SubfolderChoice,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ProjectsTags.subfolderCheck(choice.name))
                .toggleable(
                    value = checked,
                    enabled = choice.state != ChoiceState.OWN_PROJECT,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                ).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        CheckBox(checked)
        Column {
            AmText(choice.name.value, maxLines = Int.MAX_VALUE)
            note(choice.state)?.let { AmText(it, color = mutedColor()) }
        }
    }
}

@Composable
private fun CheckBox(checked: Boolean) {
    val border =
        AmTheme.colors.surfaces.border2
            .toColor()
    Box(
        modifier = Modifier.size(16.dp).border(AmTheme.shape.borderWidth, border).background(raisedColor()),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) AmText("✓")
    }
}

private fun note(state: ChoiceState): String? =
    when (state) {
        ChoiceState.AVAILABLE -> null
        ChoiceState.OWN_PROJECT -> ProjectsStrings["subfolders.ownProject"]
        ChoiceState.MISSING -> ProjectsStrings["subfolders.missing"]
    }
