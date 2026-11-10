package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmCounter
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
 * The "WIDOKI" label and the entries of the global views with their counts over every project; clicking one sends
 * [MissionControlIntent.ShowView]. The shown entry has the sel fill and an accent frame with the row radius.
 */
@Composable
public fun GlobalViews(
    controller: MissionControlController,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    Column(
        modifier = modifier.width(width = SIDEBAR_WIDTH).background(color = panelColor()).padding(all = 8.dp),
        verticalArrangement = Arrangement.spacedBy(space = 2.dp),
    ) {
        AmText(
            MissionControlStrings["views.label"],
            Modifier.padding(horizontal = 4.dp, vertical = 4.dp).testTag(MissionControlTags.VIEWS_LABEL),
            TextRole.LABEL,
            mutedColor(),
        )
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
    val (icon, tone) = lookOf(entry.view)
    val title = entry.view.title()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(MissionControlTags.view(entry.view))
                .focusProperties { canFocus = false } // else it would consume space and Enter
                .selectable(selected = entry.selected, role = Role.Tab, onClick = onClick)
                .entryFrame(entry.selected)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmIcon(icon, null, tint = tone.markColor())
        Box(modifier = Modifier.weight(weight = 1f)) { AmText(title) }
        val description = MissionControlStrings.format(UiText("view.count", listOf(title, "${entry.count}")))
        AmCounter(entry.count, tone, description)
    }
}

/** Sel fill and accent frame of the shown entry, both with the row radius. */
@Composable
private fun Modifier.entryFrame(selected: Boolean): Modifier {
    if (!selected) return this
    val shape = AmTheme.shape
    return this
        .background(
            AmTheme.colors.surfaces.sel
                .toColor(),
            shape.row,
        ).border(
            shape.borderWidth,
            AmTheme.colors.content.accent
                .toColor(),
            shape.row,
        )
}

/** The width of the projects sidebar above, so both sit in one column (plan, risks). */
private val SIDEBAR_WIDTH = 260.dp

private fun lookOf(view: GlobalView): Pair<IconKind, StatusTone> =
    when (view) {
        GlobalView.WAITING -> IconKind.WAITING to StatusTone.WARN
        GlobalView.WORKING -> IconKind.WORKING to StatusTone.WORK
        GlobalView.DONE -> IconKind.DONE to StatusTone.OK
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
    ) { GlobalHeader(page) }
}

@Composable
private fun GlobalHeader(page: Screen.Global) {
    Column(modifier = Modifier.testTag(MissionControlTags.HEADER)) {
        TitleRow { AmText(page.view.title(), Modifier.weight(weight = 1f), role = TextRole.TITLE) }
        AmText(MissionControlStrings.formatted("header.global", page.sessions), color = mutedColor())
    }
}
