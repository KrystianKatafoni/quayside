package dev.agentmanager.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.agentmanager.missioncontrol.ui.MissionControlIntent.ShowView

/**
 * Entries of the global views with their counts over every project; clicking one sends
 * [MissionControlIntent.ShowView].
 */
@Composable
public fun GlobalViews(
    controller: MissionControlController,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    Column(modifier = modifier.width(width = SIDEBAR_WIDTH).background(color = Colors.panel)) {
        state.views.forEach { entry ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(MissionControlTags.view(entry.view))
                        .focusProperties { canFocus = false } // else it would consume space and Enter
                        .selectable(selected = entry.selected, role = Role.Tab) {
                            controller.onIntent(ShowView(entry.view))
                        }.background(color = if (entry.selected) Colors.selection else Colors.panel)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            ) {
                Label(MissionControlStrings["view.${entry.view.name.lowercase()}"])
                Label("${entry.count}", Colors.of(toneOf(entry.view)), mono = true)
            }
        }
    }
}

/** The width of the projects sidebar above, so both sit in one column (plan, risks). */
private val SIDEBAR_WIDTH = 260.dp

private fun toneOf(view: GlobalView): Tone =
    when (view) {
        GlobalView.WAITING -> Tone.WARN
        GlobalView.WORKING -> Tone.WORK
        GlobalView.FAILED -> Tone.ERR
    }

@Composable
internal fun GlobalPage(
    page: Screen.Global,
    preview: PreviewUi?,
    onEvent: (MissionControlEvent) -> Unit,
) {
    SessionPage(
        page.banner,
        page.loading,
        page.rows,
        page.details,
        preview,
        onEvent,
        filters = {},
    ) { GlobalHeader(page) }
}

@Composable
private fun GlobalHeader(page: Screen.Global) {
    Column(modifier = Modifier.testTag(MissionControlTags.HEADER)) {
        Label(MissionControlStrings["view.${page.view.name.lowercase()}"], size = 18)
        Label(MissionControlStrings.formatted("header.global", page.sessions), Colors.secondary)
    }
}
