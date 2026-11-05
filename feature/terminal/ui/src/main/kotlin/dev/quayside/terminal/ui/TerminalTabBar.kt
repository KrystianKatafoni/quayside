package dev.quayside.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmIcon
import dev.quayside.core.designsystem.AmIconButton
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.IconButtonOptions
import dev.quayside.core.designsystem.IconButtonSize
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.markColor
import dev.quayside.core.designsystem.toColor

/** The bar of tabs, one group per project in the order of the first tab. [activeTab]: the tab of the active pane. */
@Composable
internal fun TerminalTabBar(
    groups: List<TabGroup>,
    activeTab: TabId?,
    statuses: Map<TabKey, TabStatus>,
    controller: TerminalController,
    onFullScreen: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(color = panelColor()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(weight = 1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            groups.forEach { TabGroupChip(it, activeTab, statuses, controller) }
        }
        onFullScreen?.let {
            AmIconButton(
                IconKind.MAXIMIZE,
                TerminalStrings["fullScreen"],
                it,
                Modifier.testTag(TerminalTags.FULL_SCREEN),
                COMPACT_UNFOCUSABLE,
            )
        }
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
        AmText(
            text = group.project.name,
            modifier = Modifier.testTag(TerminalTags.group(group.project.id)),
            color = mutedColor(),
        )
        group.tabs.forEach { TabChip(it, selected = it.id == activeTab, status = statuses[it.key], controller) }
        AmIconButton(
            IconKind.PLUS,
            TerminalStrings["group.add"],
            { controller.openShell(group.project) },
            Modifier.testTag(TerminalTags.groupAdd(group.project.id)),
            COMPACT_UNFOCUSABLE,
        )
    }
}

/**
 * A tab: the icon of its [status], its label (click shows it), the `exit N` marker of an exited process and the x
 * that closes it.
 */
@Composable
internal fun TabChip(
    tab: TerminalTab,
    selected: Boolean,
    status: TabStatus?,
    controller: TerminalController,
) {
    val surfaces = AmTheme.colors.surfaces
    val shade = (if (selected) surfaces.sel else surfaces.panel).toColor()
    Row(modifier = Modifier.background(color = shade), verticalAlignment = Alignment.CenterVertically) {
        status?.let { TabStatusMark(tab.id, it) }
        AmText(
            text = tab.label,
            modifier =
                Modifier
                    .testTag(TerminalTags.tab(tab.id))
                    .focusProperties { canFocus = false } // else it would consume Space and Enter
                    .selectable(selected = selected, role = Role.Tab) { controller.select(tab.id) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        tab.exitCode?.let { TabExitMarker(tab.id, it) }
        AmIconButton(
            IconKind.CLOSE,
            TerminalStrings["tab.close"],
            { controller.close(tab.id) },
            Modifier.testTag(TerminalTags.tabClose(tab.id)),
            COMPACT_UNFOCUSABLE,
        )
    }
}

/** The `exit N` marker of a tab whose process ended with [code]. */
@Composable
private fun TabExitMarker(
    tab: TabId,
    code: Int,
) {
    AmText(
        text = TerminalStrings.format("tab.exit", code.toString()),
        modifier = Modifier.testTag(TerminalTags.tabExit(tab)),
        role = TextRole.CODE,
        color = mutedColor(),
    )
}

/** The 14 dp mark before the label of a keyed tab: the icon of its status. Not clickable, so it never takes focus. */
@Composable
private fun TabStatusMark(
    tab: TabId,
    status: TabStatus,
) {
    val (icon, tone) = status.look()
    AmIcon(
        icon,
        TerminalStrings["tab.status.${status.name.lowercase()}"],
        Modifier.padding(start = 8.dp).size(14.dp).testTag(TerminalTags.tabStatus(tab)),
        tone.markColor(),
    )
}

/** Close, add and full screen never take focus: a focused button would eat Space and Enter of the window. */
private val COMPACT_UNFOCUSABLE = IconButtonOptions(focusable = false, size = IconButtonSize.COMPACT)
