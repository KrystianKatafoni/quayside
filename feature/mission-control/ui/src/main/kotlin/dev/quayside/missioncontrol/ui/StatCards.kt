package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmStatCard
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.StatusTone

/** Four counters of the project page: waiting, working, done, failed (stopped sessions are in none). */
@Composable
internal fun StatCards(stats: SessionStats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        Card("waiting", stats.waiting, IconKind.WAITING, StatusTone.WARN)
        Card("working", stats.working, IconKind.WORKING, StatusTone.WORK)
        Card("done", stats.done, IconKind.DONE, StatusTone.OK)
        Card("failed", stats.failed, IconKind.FAILED, StatusTone.ERR)
    }
}

@Composable
private fun RowScope.Card(
    kind: String,
    count: Int,
    icon: IconKind,
    tone: StatusTone,
) {
    AmStatCard(
        value = "$count",
        label = MissionControlStrings["stat.$kind"],
        icon = icon,
        modifier = Modifier.weight(weight = 1f).testTag(MissionControlTags.stat(kind)),
        tone = tone,
    )
}
