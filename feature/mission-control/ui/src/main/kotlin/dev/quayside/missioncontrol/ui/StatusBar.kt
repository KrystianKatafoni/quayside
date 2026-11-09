package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmModeBadge
import dev.quayside.core.designsystem.AmModeBar
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.StatusTone
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.markColor

/**
 * Window status bar: the `NORMAL` or `TERMINAL` badge ([terminal]), the selected project (none in a global view), the
 * counters of every project, then [trailing] (e.g. the notification status), then the freshness dot and
 * "supervisor [supervisor]" (the freshness text while [supervisor] is null). Drawn by `app` in both modes (AM-816).
 */
@Composable
public fun StatusBar(
    controller: MissionControlController,
    terminal: Boolean,
    supervisor: String?,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val bar =
        controller.state
            .collectAsState()
            .value.modeBar
    AmModeBar(modifier = modifier.fillMaxWidth().testTag(MissionControlTags.MODE_BAR)) {
        AmModeBadge(if (terminal) TERMINAL_MODE else bar.mode)
        bar.project?.let { AmText(it, role = TextRole.CAPTION, color = textColor()) }
        Counter(MissionControlStrings.plural("modeBar.waiting", bar.waiting), StatusTone.WARN)
        Counter(MissionControlStrings.plural("modeBar.working", bar.working), StatusTone.WORK)
        Spacer(Modifier.weight(weight = 1f))
        trailing()
        FreshnessDot(Freshness.tone(bar.freshness))
        val text = supervisor?.let { MissionControlStrings.formatted("modeBar.supervisor", it) }
        AmText(text ?: MissionControlStrings.format(bar.freshness), role = TextRole.CAPTION)
    }
}

private const val TERMINAL_MODE = "TERMINAL"

@Composable
private fun Counter(
    text: String,
    tone: StatusTone,
) {
    AmText(text, role = TextRole.CAPTION, color = tone.markColor())
}

@Composable
private fun FreshnessDot(tone: StatusTone) {
    Box(Modifier.size(DOT).testTag(MissionControlTags.STATUS_DOT).background(tone.markColor(), CircleShape))
}

private val DOT = 8.dp
