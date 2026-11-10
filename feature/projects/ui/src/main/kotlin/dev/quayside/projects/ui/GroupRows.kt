package dev.quayside.projects.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmIcon
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.markColor
import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.SubfolderName

/**
 * The arrow of a group row: hides or shows the subfolder rows. Never focusable (a focused clickable would eat Space
 * and Enter of the window), its label says what the click does.
 */
@Composable
internal fun GroupChevron(
    id: ProjectId,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val label = ProjectsStrings[if (collapsed) "group.expand" else "group.collapse"]
    Box(
        modifier =
            Modifier
                .testTag(ProjectsTags.chevron(id))
                .focusProperties { canFocus = false }
                .clip(AmTheme.shape.row)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { contentDescription = label }
                .padding(horizontal = 4.dp),
    ) {
        AmText(if (collapsed) "▸" else "▾", Modifier.clearAndSetSemantics {}, color = mutedColor())
    }
}

/**
 * The row of a subfolder under its group row: indented, with a small folder, the name and the counters of the
 * sessions in it. Like [ProjectRow] it never takes focus; it has no children that could.
 */
@Composable
internal fun SubfolderRow(
    id: ProjectId,
    name: SubfolderName,
    state: RowState,
    onSelect: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ProjectsTags.subfolderRow(id, name))
                .focusProperties { canFocus = false }
                // Never takes focus (above); only exposes the `Focused` semantics so tests can prove it.
                .focusable()
                .semantics {
                    selected = state.selected
                    text = AnnotatedString(name.value)
                }.selectedFrame(state.selected),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .focusProperties { canFocus = false }
                .clip(AmTheme.shape.row)
                .selectable(selected = state.selected, onClick = onSelect),
        )
        SubfolderCells(name, state)
    }
}

@Composable
private fun SubfolderCells(
    name: SubfolderName,
    state: RowState,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        AmIcon(IconKind.FOLDER, null, Modifier.size(16.dp), projectTone(state.statuses.active).markColor())
        Column(modifier = Modifier.weight(weight = 1f), verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
            AmText(name.value, Modifier.clearAndSetSemantics {})
        }
    }
}

/** What the ⋯ menu and the clicks of a project row do; the rest of the row is [RowState]. */
internal class RowActions(
    val select: () -> Unit,
    val rename: () -> Unit,
    val remove: () -> Unit,
    val subfolders: () -> Unit,
    val toggleGroup: () -> Unit,
)

/** What a row shows besides its name: selection, the counters, and for a group row whether it is collapsed. */
internal data class RowState(
    val selected: Boolean,
    val statuses: StatusCounts,
    val collapsed: Boolean? = null,
)
