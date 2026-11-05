package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmChip
import dev.quayside.core.designsystem.AmIconButton
import dev.quayside.core.designsystem.AmIconTile
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.ButtonKind
import dev.quayside.core.designsystem.ButtonOptions
import dev.quayside.core.designsystem.IconButtonOptions
import dev.quayside.core.designsystem.IconButtonStyle
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.domain.SessionId

/**
 * The scrolling list of rows. The row menu is open for at most one row, the one it was opened on; another selection
 * (j/k, a click elsewhere) closes it for good.
 */
@Composable
internal fun SessionRows(
    rows: List<SessionRow>,
    details: SessionDetails?,
    onEvent: (MissionControlEvent) -> Unit,
    modifier: Modifier,
) {
    var menuFor by remember { mutableStateOf<SessionId?>(value = null) }
    val selectedId = rows.firstOrNull { it.selected }?.id
    LaunchedEffect(selectedId) { if (menuFor != selectedId) menuFor = null }
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        rows.forEach { row ->
            val shown = details?.takeIf { row.selected && it.id == row.id }
            val menu =
                RowMenu(shown?.allControls().orEmpty(), open = menuFor == row.id) { open ->
                    menuFor = if (open) row.id else null
                }
            SessionRowView(row, shown, menu, onEvent)
        }
    }
}

/**
 * A row never takes focus: a focused `clickable` handles `space` and Enter as a click and consumes them before the
 * window shortcuts (`space` preview, later `⏎` attach) see them. A click requests focus anyway, and an unfocusable
 * node hands it to its first focusable child, so the children (the buttons) must not be focusable either. The click
 * is taken by a layer behind the cells, so the tile and the buttons are separate nodes of the semantics tree. The
 * first click of a double click has already selected the row, so `attachSelected` in `app` reads the right session;
 * `select()` in the double-click path is only a guard. A click selects at once (no wait for a double click); a
 * double click also attaches, a right click and "⋯" select and open the [menu] (AM-511).
 */
@Composable
private fun SessionRowView(
    row: SessionRow,
    shown: SessionDetails?,
    menu: RowMenu,
    onEvent: (MissionControlEvent) -> Unit,
) {
    val select = { onEvent(MissionControlEvent.SelectSession(row.id)) }
    val rowMenu =
        RowMenu(menu.controls, menu.open) { open ->
            if (open) select()
            menu.setOpen(open)
        }
    val attach = {
        select()
        onEvent(MissionControlEvent.Attach)
    }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(MissionControlTags.row(row.id))
                .onRowMouse(row.id, onSecondary = { rowMenu.setOpen(true) }, onDouble = attach)
                .background(color = if (row.selected) selColor() else Color.Transparent),
    ) {
        Box(Modifier.matchParentSize().focusProperties { canFocus = false }.clickable(onClick = select))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
        ) {
            RowCells(row)
            RowButtons(row.id, shown, rowMenu, onEvent)
        }
    }
}

@Composable
private fun RowScope.RowCells(row: SessionRow) {
    AmIconTile(row.glyph.icon, row.glyph.tone, MissionControlStrings[row.glyph.key], Modifier.size(TILE))
    Column(modifier = Modifier.weight(weight = 1f)) {
        AmText(row.name, role = TextRole.CODE)
        AmText(row.summary.orEmpty(), role = TextRole.CAPTION, color = mutedColor())
    }
    row.project?.let { AmText(MissionControlStrings.format(it), color = mutedColor()) }
    AmChip(MissionControlStrings.format(row.lastEvent), tone = row.glyph.tone)
    AmText(MissionControlStrings.format(row.age), color = mutedColor())
}

/** Reply and Attach appear in the selected row only; none of the buttons takes focus. */
@Composable
private fun RowButtons(
    id: SessionId,
    shown: SessionDetails?,
    menu: RowMenu,
    onEvent: (MissionControlEvent) -> Unit,
) {
    if (shown?.canReply == true) {
        AmButton(
            MissionControlStrings["action.reply"],
            { onEvent(MissionControlEvent.Reply) },
            Modifier.testTag(MissionControlTags.rowReply(id)),
            ButtonOptions(kind = ButtonKind.PRIMARY, focusable = false),
        )
    }
    if (shown?.canAttach == true) {
        AmIconButton(
            IconKind.ATTACH,
            MissionControlStrings["action.attach"],
            { onEvent(MissionControlEvent.Attach) },
            Modifier.testTag(MissionControlTags.rowAttach(id)),
            IconButtonOptions(focusable = false, style = IconButtonStyle.OUTLINED),
        )
    }
    RowMenuButton(MissionControlTags.rowMenuButton(id), menu, onEvent)
}

private val TILE = 38.dp
