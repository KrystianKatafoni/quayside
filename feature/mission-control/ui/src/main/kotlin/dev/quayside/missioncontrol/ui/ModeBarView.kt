package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.quayside.core.designsystem.AmModeBadge
import dev.quayside.core.designsystem.AmModeBar
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.TextRole

/**
 * `NORMAL`, the selected project (none in a global view), the counters of every project and the freshness of the
 * snapshot; at the end the "Skróty" toggle of the cheat sheet, which opens above the bar (closed at start).
 */
@Composable
internal fun ModeBarView(bar: ModeBar) {
    var shortcuts by remember { mutableStateOf(value = false) }
    if (shortcuts) ShortcutsPanel()
    AmModeBar(modifier = Modifier.fillMaxWidth().testTag(MissionControlTags.MODE_BAR)) {
        AmModeBadge(bar.mode)
        bar.project?.let {
            AmText(
                it,
                role = TextRole.CAPTION,
                color = textColor(),
            )
        }
        ModeCounter(MissionControlStrings.formatted("modeBar.waiting", bar.waiting), Tone.WARN)
        ModeCounter(MissionControlStrings.formatted("modeBar.working", bar.working), Tone.WORK)
        Box(modifier = Modifier.weight(weight = 1f)) {
            AmText(MissionControlStrings.format(bar.freshness), role = TextRole.CAPTION)
        }
        ShortcutsToggle(shortcuts) { shortcuts = !shortcuts }
    }
}

@Composable
private fun ModeCounter(
    text: String,
    tone: Tone,
) {
    AmText(text, role = TextRole.CAPTION, color = tone.tint())
}
