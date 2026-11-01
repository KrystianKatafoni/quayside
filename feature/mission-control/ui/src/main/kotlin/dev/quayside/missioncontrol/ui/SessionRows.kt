package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
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
            val controls = details?.takeIf { row.selected && it.id == row.id }?.let(SessionDetails::allControls)
            val menu =
                RowMenu(controls.orEmpty(), open = menuFor == row.id) { open -> menuFor = if (open) row.id else null }
            SessionRowView(row, menu, onEvent)
        }
    }
}

/**
 * A row never takes focus: a focused `clickable` handles `space` and Enter as a click and consumes them before the
 * window shortcuts (`space` preview, later `⏎` attach) see them. A click requests focus anyway, and an unfocusable
 * node hands it to its first focusable child, so the children (the texts and the "⋯" button) must not be focusable
 * either. The first click of a double click has already selected the row, so `attachSelected` in `app` reads the
 * right session; `select()` in the double-click path is only a guard. A click selects at once (no wait for a
 * double click); a double click also attaches, a right click and "⋯" select and open the [menu] (AM-511).
 */
@Composable
private fun SessionRowView(
    row: SessionRow,
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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(MissionControlTags.row(row.id))
                .focusProperties { canFocus = false }
                .onRowMouse(row.id, onSecondary = { rowMenu.setOpen(true) }, onDouble = attach)
                .clickable(onClick = select)
                .background(color = if (row.selected) Colors.selection else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        RowCells(row)
        RowMenuButton(MissionControlTags.rowMenuButton(row.id), rowMenu, onEvent)
    }
}

@Composable
private fun RowScope.RowCells(row: SessionRow) {
    Box(
        Modifier
            .size(
                width = 3.dp,
                height = 28.dp,
            ).background(if (row.selected) Colors.accent else Color.Transparent),
    )
    Label(row.glyph.symbol, Colors.of(row.glyph.tone))
    Label(row.name, mono = true)
    row.project?.let { Label(MissionControlStrings.format(it), Colors.secondary) }
    Box(modifier = Modifier.weight(weight = 1f)) { Label(row.summary.orEmpty(), Colors.secondary) }
    Label(MissionControlStrings.format(row.lastEvent), Colors.secondary)
    Label(MissionControlStrings.format(row.age), Colors.secondary)
}
