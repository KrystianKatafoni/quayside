package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmChip
import dev.quayside.core.designsystem.AmIcon
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.StatusTone
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.toColor
import dev.quayside.projects.api.ProjectFocus

/**
 * The "Podfoldery" section of a group page (FR-P8): a scrolling card with one row per subfolder (no row for the main
 * folder). Every row has chips (icon and count) for waiting, working and failed; a state at zero has none. A click
 * on a row sends [MissionControlEvent.OpenSubfolder]; rows never take the keyboard focus, so Space and Enter stay
 * window shortcuts.
 */
@Composable
internal fun GroupOverviewView(
    overview: GroupOverview,
    onEvent: (MissionControlEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(space = 6.dp)) {
        AmText(MissionControlStrings["overview.title"], role = TextRole.CAPTION, color = mutedColor())
        OverviewCard {
            overview.rows.forEach { row ->
                val focus = ProjectFocus(overview.project, row.name)
                OverviewRow(
                    name = row.name.value,
                    stats = row.stats,
                    modifier =
                        Modifier
                            .testTag(MissionControlTags.overviewRow(row.name))
                            .focusProperties { canFocus = false }
                            .clickable(role = Role.Button) { onEvent(MissionControlEvent.OpenSubfolder(focus)) },
                )
            }
        }
    }
}

/** The framed panel of the rows: at most [MAX_HEIGHT] high, scrolling (not focusable) beyond that. */
@Composable
private fun OverviewCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = AmTheme.shape
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = MAX_HEIGHT)
                .testTag(MissionControlTags.OVERVIEW)
                .clip(shape.card)
                .background(panelColor(), shape.card)
                .border(
                    shape.borderWidth,
                    AmTheme.colors.surfaces.border
                        .toColor(),
                    shape.card,
                ).verticalScroll(rememberScrollState()),
        content = content,
    )
}

@Composable
private fun OverviewRow(
    name: String,
    stats: SessionStats,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        AmIcon(IconKind.FOLDER, null, Modifier.size(16.dp), mutedColor())
        AmText(name, Modifier.weight(weight = 1f))
        OverviewCells(stats)
    }
}

@Composable
private fun OverviewCells(stats: SessionStats) {
    chips(stats).forEach { chip ->
        AmChip(label = chip.count.toString(), tone = chip.tone, icon = chip.icon, description = chip.description)
    }
}

private data class OverviewChip(
    val count: Int,
    val tone: StatusTone,
    val icon: IconKind,
    val description: String,
)

/** Waiting, working, failed: the order of the sidebar; a state with no sessions has no chip. */
private fun chips(stats: SessionStats): List<OverviewChip> =
    listOf(
        Triple(stats.waiting, StatusTone.WARN to IconKind.WAITING, "overview.count.waiting"),
        Triple(stats.working, StatusTone.WORK to IconKind.WORKING, "overview.count.working"),
        Triple(stats.failed, StatusTone.ERR to IconKind.FAILED, "overview.count.failed"),
    ).filter { (count, _, _) -> count > 0 }
        .map { (count, look, key) ->
            OverviewChip(count, look.first, look.second, MissionControlStrings.formatted(key, count))
        }

private val MAX_HEIGHT = 168.dp
