package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.TextRole
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun Details(
    details: SessionDetails?,
    onEvent: (MissionControlEvent) -> Unit,
) {
    val panel =
        Modifier
            .width(width = 300.dp)
            .fillMaxHeight()
            .background(color = panelColor())
            .padding(all = 12.dp)
    if (details == null) {
        Box(modifier = panel.testTag(MissionControlTags.DETAILS_EMPTY), contentAlignment = Alignment.Center) {
            AmText(MissionControlStrings["details.none"], color = mutedColor())
        }
        return
    }
    Column(
        modifier = panel.testTag(MissionControlTags.DETAILS),
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        AmText(details.name, role = TextRole.CODE)
        Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
            AmText(details.glyph.symbol, color = details.glyph.tone.tint())
            AmText(MissionControlStrings[details.glyph.key])
        }
        details.summary?.let { AmText(it) }
        SessionActionsView(details, onEvent)
        Field("details.worktree", details.worktree.toString())
        Field("details.id", details.id.value)
        Field("details.startedAt", timeFormat.format(details.startedAt))
        Field("details.lastActivity", timeFormat.format(details.lastActivityAt))
    }
}

@Composable
private fun Field(
    key: String,
    value: String,
) {
    Column {
        AmText(MissionControlStrings[key], color = mutedColor())
        AmText(value, role = TextRole.CODE)
    }
}

/** Start and last activity in the details panel, in the zone of the Mac. */
private val timeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
