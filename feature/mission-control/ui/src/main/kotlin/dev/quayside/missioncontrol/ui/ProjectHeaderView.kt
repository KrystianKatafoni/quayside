package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmChip
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.ButtonKind
import dev.quayside.core.designsystem.ButtonOptions
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.toColor

/** Title, path chip, session count and "Nowy agent" (PRIMARY, never takes focus) of the project page. */
@Composable
internal fun ProjectHeaderView(
    header: ProjectHeader,
    onEvent: (MissionControlEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(MissionControlTags.HEADER),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmText(header.name, role = TextRole.TITLE)
        AmChip(
            header.path.toString(),
            Modifier.weight(weight = 1f, fill = false).testTag(MissionControlTags.HEADER_PATH).merged(),
            icon = IconKind.FOLDER,
        )
        AmChip(
            MissionControlStrings.formatted("header.sessions", header.sessions),
            Modifier.testTag(MissionControlTags.HEADER_COUNT).merged(),
        )
        Row(Modifier.weight(weight = 1f), horizontalArrangement = Arrangement.End) { NewAgentButton(onEvent) }
    }
}

private fun Modifier.merged(): Modifier = semantics(mergeDescendants = true) {}

@Composable
private fun NewAgentButton(onEvent: (MissionControlEvent) -> Unit) {
    AmButton(
        MissionControlStrings["newAgent.button"],
        { onEvent(MissionControlEvent.NewAgent) },
        Modifier.testTag(MissionControlTags.NEW_AGENT),
        ButtonOptions(
            kind = ButtonKind.PRIMARY,
            focusable = false,
            icon = IconKind.PLUS,
            shortcut = LocalKeyCaps.current?.newAgent,
        ),
    )
}

/** Filter segments (C); they never take focus, like the session rows. */
@Composable
internal fun Filters(
    current: SessionFilter,
    onEvent: (MissionControlEvent) -> Unit,
) {
    val shape = AmTheme.shape
    Row(
        modifier =
            Modifier.border(
                shape.borderWidth,
                AmTheme.colors.surfaces.border2
                    .toColor(),
                shape.corner,
            ),
    ) {
        SessionFilter.entries.forEach { filter ->
            val fill = if (filter == current) AmTheme.colors.surfaces.sel else AmTheme.colors.surfaces.raised
            Row(
                modifier =
                    Modifier
                        .testTag(MissionControlTags.filter(filter))
                        .focusProperties { canFocus = false }
                        .clip(shape.corner)
                        .selectable(selected = filter == current, role = Role.Tab) {
                            onEvent(MissionControlEvent.SelectFilter(filter))
                        }.background(fill.toColor())
                        .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                AmText(MissionControlStrings["filter.${filter.name.lowercase()}"])
            }
        }
    }
}
