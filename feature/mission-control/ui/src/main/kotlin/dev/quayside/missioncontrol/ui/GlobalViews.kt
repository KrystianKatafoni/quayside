package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmChip
import dev.quayside.core.designsystem.AmIcon
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.StatusTone
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.markColor
import dev.quayside.core.designsystem.toColor
import dev.quayside.missioncontrol.ui.MissionControlIntent.ShowView

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
    Column(modifier = modifier.width(width = SIDEBAR_WIDTH).background(color = panelColor())) {
        state.views.forEach { entry ->
            ViewEntryRow(entry) { controller.onIntent(ShowView(entry.view)) }
        }
    }
}

@Composable
private fun ViewEntryRow(
    entry: ViewEntry,
    onClick: () -> Unit,
) {
    val surfaces = AmTheme.colors.surfaces
    val (icon, tone) = lookOf(entry.view)
    val title = MissionControlStrings["view.${entry.view.name.lowercase()}"]
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(MissionControlTags.view(entry.view))
                .focusProperties { canFocus = false } // else it would consume space and Enter
                .selectable(selected = entry.selected, role = Role.Tab, onClick = onClick)
                .background(color = (if (entry.selected) surfaces.sel else surfaces.panel).toColor())
                .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmIcon(icon, null, tint = tone.markColor())
        Box(modifier = Modifier.weight(weight = 1f)) { AmText(title) }
        // The description is added next to the text (AmChip.description would hide the count from the entry text).
        val description = MissionControlStrings.format(UiText("view.count", listOf(title, "${entry.count}")))
        AmChip("${entry.count}", Modifier.semantics { contentDescription = description }, tone)
    }
}

/** The width of the projects sidebar above, so both sit in one column (plan, risks). */
private val SIDEBAR_WIDTH = 260.dp

private fun lookOf(view: GlobalView): Pair<IconKind, StatusTone> =
    when (view) {
        GlobalView.WAITING -> IconKind.WAITING to StatusTone.WARN
        GlobalView.WORKING -> IconKind.WORKING to StatusTone.WORK
        GlobalView.FAILED -> IconKind.FAILED to StatusTone.ERR
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
        AmText(MissionControlStrings["view.${page.view.name.lowercase()}"], role = TextRole.TITLE)
        AmText(MissionControlStrings.formatted("header.global", page.sessions), color = mutedColor())
    }
}
