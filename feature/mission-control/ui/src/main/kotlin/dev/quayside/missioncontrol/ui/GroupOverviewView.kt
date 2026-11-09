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
import dev.quayside.core.designsystem.AmCounter
import dev.quayside.core.designsystem.AmIcon
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.StatusTone
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.toColor
import dev.quayside.projects.api.ProjectFocus

/**
 * The "Podfoldery" section of a group page (FR-P8): a scrolling card with one row per subfolder, and a last row
 * "Pozostałe" when sessions outside the subfolders need attention. A click on a subfolder row sends
 * [MissionControlEvent.OpenSubfolder]; rows never take the keyboard focus, so Space and Enter stay window shortcuts.
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
            overview.rest?.let {
                OverviewRow(
                    MissionControlStrings["overview.rest"],
                    it,
                    Modifier.testTag(MissionControlTags.OVERVIEW_REST),
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
    val counters = counters(stats)
    if (counters.isEmpty()) {
        AmText(MissionControlStrings["overview.idle"], color = mutedColor())
    }
    counters.forEach { (count, tone, description) -> AmCounter(count, tone, description) }
}

private fun counters(stats: SessionStats): List<Triple<Int, StatusTone, String>> =
    listOf(
        Triple(stats.waiting, StatusTone.WARN, MissionControlStrings.plural("overview.waiting", stats.waiting)),
        Triple(stats.failed, StatusTone.ERR, MissionControlStrings.formatted("overview.failed", stats.failed)),
        Triple(stats.working, StatusTone.WORK, MissionControlStrings.plural("overview.working", stats.working)),
    ).filter { it.first > 0 }

private val MAX_HEIGHT = 168.dp
