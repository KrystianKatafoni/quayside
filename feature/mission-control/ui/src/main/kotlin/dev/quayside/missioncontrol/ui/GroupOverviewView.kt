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
 * "folder główny" (sessions outside the subfolders, always shown, named like the badge in the list). Every row has
 * four counters; zeros are rings. A click on a subfolder row sends [MissionControlEvent.OpenSubfolder], the root row
 * does nothing; rows never take the keyboard focus, so Space and Enter stay window shortcuts.
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
            OverviewRow(
                MissionControlStrings["overview.root"],
                overview.rest,
                Modifier.testTag(MissionControlTags.OVERVIEW_ROOT),
            )
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
    counters(stats).forEach { (count, tone, description) ->
        if (count == 0) ZeroCounter(tone, description, selected = false) else AmCounter(count, tone, description)
    }
}

/** Waiting, working, done, failed: the order of the sidebar; each described as "czeka: n" and so on. */
private fun counters(stats: SessionStats): List<Triple<Int, StatusTone, String>> =
    listOf(
        Triple(stats.waiting, StatusTone.WARN, "overview.count.waiting"),
        Triple(stats.working, StatusTone.WORK, "overview.count.working"),
        Triple(stats.done, StatusTone.OK, "overview.count.done"),
        Triple(stats.failed, StatusTone.ERR, "overview.count.failed"),
    ).map { (count, tone, key) -> Triple(count, tone, MissionControlStrings.formatted(key, count)) }

private val MAX_HEIGHT = 168.dp
