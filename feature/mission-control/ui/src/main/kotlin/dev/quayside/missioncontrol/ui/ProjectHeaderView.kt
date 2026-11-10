package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmChip
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.toColor

/**
 * Two rows of the project page: the title; then the path chip, the session count and, at the right edge, the filter
 * segments ([filter] is the selected one).
 */
@Composable
internal fun ProjectHeaderView(
    header: ProjectHeader,
    filter: SessionFilter,
    onEvent: (MissionControlEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(MissionControlTags.HEADER),
        verticalArrangement = Arrangement.spacedBy(space = 12.dp),
    ) {
        TitleRow { AmText(header.name, Modifier.weight(weight = 1f), role = TextRole.TITLE) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderChips(header, Modifier.weight(weight = 1f))
            Filters(filter, onEvent)
        }
    }
}

/** A page title with the "Nowy agent" button at the right edge, on the same line. */
@Composable
internal fun TitleRow(title: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        title()
        NewAgentButton()
    }
}

/** The path chip, the branch chip when known and the session count. */
@Composable
private fun HeaderChips(
    header: ProjectHeader,
    modifier: Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmChip(
            header.path.toString(),
            Modifier.weight(weight = 1f, fill = false).testTag(MissionControlTags.HEADER_PATH).merged(),
            icon = IconKind.FOLDER,
        )
        header.branch?.let {
            AmChip(it, Modifier.testTag(MissionControlTags.HEADER_BRANCH).merged(), icon = IconKind.BRANCH)
        }
        AmChip(
            MissionControlStrings.formatted("header.sessions", header.sessions),
            Modifier.testTag(MissionControlTags.HEADER_COUNT).merged(),
        )
    }
}

private fun Modifier.merged(): Modifier = semantics(mergeDescendants = true) {}

/**
 * Filter segments in a raised container with a frame; the selected one is on sel. All have the row radius and never
 * take focus, like the session rows.
 */
@Composable
internal fun Filters(
    current: SessionFilter,
    onEvent: (MissionControlEvent) -> Unit,
) {
    val shape = AmTheme.shape
    Row(
        modifier =
            Modifier
                .clip(shape.row)
                .background(
                    AmTheme.colors.surfaces.raised
                        .toColor(),
                ).border(
                    shape.borderWidth,
                    AmTheme.colors.surfaces.border2
                        .toColor(),
                    shape.row,
                ),
    ) {
        SessionFilter.entries.forEach { filter -> Segment(filter, filter == current, onEvent) }
    }
}

@Composable
private fun Segment(
    filter: SessionFilter,
    selected: Boolean,
    onEvent: (MissionControlEvent) -> Unit,
) {
    val surfaces = AmTheme.colors.surfaces
    val fill = if (selected) surfaces.sel else surfaces.raised
    Row(
        modifier =
            Modifier
                .testTag(MissionControlTags.filter(filter))
                .focusProperties { canFocus = false }
                .clip(AmTheme.shape.row)
                .selectable(selected = selected, role = Role.Tab) { onEvent(MissionControlEvent.SelectFilter(filter)) }
                .background(fill.toColor())
                .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        AmText(MissionControlStrings["filter.${filter.name.lowercase()}"])
    }
}
