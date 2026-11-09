package dev.quayside.projects.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmCounter
import dev.quayside.core.designsystem.AmIcon
import dev.quayside.core.designsystem.AmKeyCap
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.StatusTone
import dev.quayside.core.designsystem.markColor
import dev.quayside.core.designsystem.toColor
import dev.quayside.projects.api.Project

/**
 * The row never takes focus: a focused `selectable` would consume `space` and Enter before the window shortcuts. A
 * click requests focus anyway and an unfocusable parent hands it to its first focusable child, so the ⋯ button of the
 * row is unfocusable too. The selection is taken by a layer behind the cells, so the counters are separate nodes of
 * the semantics tree; the tagged node itself carries the name and says whether the row is selected. The selected row
 * has the sel fill and an accent frame, both with the row radius (r+2).
 */
@Composable
internal fun ProjectRow(
    project: Project,
    state: RowState,
    actions: RowActions,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ProjectsTags.row(project.id))
                .semantics {
                    this.selected = state.selected
                    text = AnnotatedString(project.displayName)
                }.selectedFrame(state.selected),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .focusProperties { canFocus = false }
                .clip(AmTheme.shape.row) // the hover overlay of the click layer must not square the corners
                .selectable(selected = state.selected, onClick = actions.select),
        )
        RowCells(project, state, actions)
    }
}

@Composable
internal fun Modifier.selectedFrame(selected: Boolean): Modifier {
    if (!selected) return this
    val shape = AmTheme.shape
    return this
        .background(selColor(), shape.row)
        .border(
            shape.borderWidth,
            AmTheme.colors.content.accent
                .toColor(),
            shape.row,
        )
}

/** Arrow of a group, folder in the tone of the project, its name over the counters, and the ⋯ menu. */
@Composable
private fun RowCells(
    project: Project,
    state: RowState,
    actions: RowActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        state.collapsed?.let { GroupChevron(project.id, it, actions.toggleGroup) }
        AmIcon(IconKind.FOLDER, null, Modifier.size(20.dp), projectTone(state.statuses.active).markColor())
        Column(modifier = Modifier.weight(weight = 1f), verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
            AmText(project.displayName, Modifier.clearAndSetSemantics {})
            Counters(state.statuses, state.selected)
        }
        RowMenu(project, actions)
    }
}

/**
 * The four counters of a row: waiting (warn, it needs the user), working, done, failed. A non-zero one is an
 * `AmCounter`, a zero one a ring over the background of the row ([selected] picks sel or panel).
 */
@Composable
internal fun Counters(
    counts: StatusCounts,
    selected: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
        CountBadges.of(counts).forEach { badge ->
            val number = badge.args.first().toInt()
            val tone = COUNTER_TONES.getValue(badge.key)
            val description = ProjectsStrings.format(badge)
            if (number == 0) ZeroCounter(tone, description, selected) else AmCounter(number, tone, description)
        }
    }
}

private val COUNTER_TONES =
    mapOf(
        CountBadges.WAITING_KEY to StatusTone.WARN,
        "row.working" to StatusTone.WORK,
        "row.done" to StatusTone.OK,
        "row.failed" to StatusTone.ERR,
    )

/**
 * "Dodaj projekt z dysku…" with its shortcut [keyCap] (none when null): a dashed button under the project list. It
 * never takes focus, so Space and Enter go on to the window.
 */
@Composable
internal fun AddFromDiskButton(
    keyCap: String?,
    onClick: () -> Unit,
) {
    val shape = AmTheme.shape
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ProjectsTags.ADD_FROM_DISK)
                .focusProperties { canFocus = false }
                .clip(shape.row)
                .clickable(role = Role.Button, onClick = onClick)
                .dashedFrame(
                    AmTheme.colors.surfaces.border2
                        .toColor(),
                    shape.radii.row,
                    shape.borderWidth,
                ).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmText(ProjectsStrings["sidebar.addFromDisk"], Modifier.weight(weight = 1f), color = mutedColor())
        keyCap?.let { AmKeyCap(it) }
    }
}

private fun Modifier.dashedFrame(
    color: Color,
    radius: Dp,
    width: Dp,
): Modifier =
    drawBehind {
        val stroke = width.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), GAP.toPx()))
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(stroke, pathEffect = dash),
        )
    }

private val DASH = 6.dp
private val GAP = 4.dp
