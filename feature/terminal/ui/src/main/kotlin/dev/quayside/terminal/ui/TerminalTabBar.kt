package dev.quayside.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** The bar of tabs, one group per project in the order of the first tab. [activeTab]: the tab of the active pane. */
@Composable
internal fun TerminalTabBar(
    groups: List<TabGroup>,
    activeTab: TabId?,
    statuses: Map<TabKey, TabStatus>,
    controller: TerminalController,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = TerminalColors.panel)
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        groups.forEach { TabGroupChip(it, activeTab, statuses, controller) }
    }
}

/** The name of a project, its tabs and the "+" that opens a shell in that project. */
@Composable
internal fun TabGroupChip(
    group: TabGroup,
    activeTab: TabId?,
    statuses: Map<TabKey, TabStatus>,
    controller: TerminalController,
) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(
            text = group.project.name,
            modifier = Modifier.testTag(TerminalTags.group(group.project.id)),
            color = TerminalColors.secondary,
        )
        group.tabs.forEach { TabChip(it, selected = it.id == activeTab, status = statuses[it.key], controller) }
        GlyphButton(TerminalTags.groupAdd(group.project.id), glyph = "+") { controller.openShell(group.project) }
    }
}

/**
 * A tab: the dot of its [status], its label (click shows it), the `exit N` marker of an exited process and the x
 * that closes it.
 */
@Composable
internal fun TabChip(
    tab: TerminalTab,
    selected: Boolean,
    status: TabStatus?,
    controller: TerminalController,
) {
    val shade = if (selected) TerminalColors.selection else TerminalColors.panel
    Row(modifier = Modifier.background(color = shade), verticalAlignment = Alignment.CenterVertically) {
        status?.let { TabStatusDot(tab.id, it) }
        Label(
            text = tab.label,
            modifier =
                Modifier
                    .testTag(TerminalTags.tab(tab.id))
                    .focusProperties { canFocus = false } // else it would consume Space and Enter
                    .selectable(selected = selected, role = Role.Tab) { controller.select(tab.id) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        tab.exitCode?.let { TabExitMarker(tab.id, it) }
        GlyphButton(TerminalTags.tabClose(tab.id), glyph = "×") { controller.close(tab.id) }
    }
}

/** The `exit N` marker of a tab whose process ended with [code]. */
@Composable
private fun TabExitMarker(
    tab: TabId,
    code: Int,
) {
    Label(
        text = TerminalStrings.format("tab.exit", code.toString()),
        modifier = Modifier.testTag(TerminalTags.tabExit(tab)),
        color = TerminalColors.secondary,
        mono = true,
    )
}

/** The dot before the label of a keyed tab. Not clickable, so it never takes the focus (AM-510). */
@Composable
private fun TabStatusDot(
    tab: TabId,
    status: TabStatus,
) {
    val text = TerminalStrings["tab.status.${status.name.lowercase()}"]
    Box(
        modifier =
            Modifier
                .padding(start = 8.dp)
                .size(8.dp)
                .background(color = TerminalColors.of(status), shape = CircleShape)
                .testTag(TerminalTags.tabStatus(tab))
                .semantics { stateDescription = text },
    )
}
