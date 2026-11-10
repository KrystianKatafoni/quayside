package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmChip
import dev.quayside.core.designsystem.AmIconButton
import dev.quayside.core.designsystem.AmIconTile
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.ButtonKind
import dev.quayside.core.designsystem.ButtonOptions
import dev.quayside.core.designsystem.IconButtonOptions
import dev.quayside.core.designsystem.IconButtonStyle
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.listMetrics
import dev.quayside.core.designsystem.toColor
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
    older: Int = 0,
) {
    var menuFor by remember { mutableStateOf<SessionId?>(value = null) }
    val selectedId = rows.firstOrNull { it.selected }?.id
    LaunchedEffect(selectedId) { if (menuFor != selectedId) menuFor = null }
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        if (rows.isNotEmpty() || older > 0) {
            ListCard {
                rows.forEachIndexed { index, row ->
                    if (index > 0) Separator()
                    val shown = details?.takeIf { row.selected && it.id == row.id }
                    val menu =
                        RowMenu(shown?.allControls().orEmpty(), open = menuFor == row.id) { open ->
                            menuFor = if (open) row.id else null
                        }
                    SessionRowView(row, shown, menu, onEvent)
                }
                if (older > 0) {
                    if (rows.isNotEmpty()) Separator()
                    OlderRow(older, onEvent)
                }
            }
        }
    }
}

/** The rows as one card: panel fill, the theme frame and the card radius (r+4). */
@Composable
private fun ListCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = AmTheme.shape
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(MissionControlTags.LIST_CARD)
                .clip(shape.card)
                .background(panelColor())
                .border(
                    shape.borderWidth,
                    AmTheme.colors.surfaces.border
                        .toColor(),
                    shape.card,
                ),
        content = content,
    )
}

@Composable
private fun Separator() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(AmTheme.shape.borderWidth)
            .background(
                AmTheme.colors.surfaces.border
                    .toColor(),
            ),
    )
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
                .semantics { selected = row.selected }
                .onRowMouse(row.id, onSecondary = { rowMenu.setOpen(true) }, onDouble = attach)
                .background(color = if (row.selected) selColor() else Color.Transparent),
    ) {
        Box(Modifier.matchParentSize().focusProperties { canFocus = false }.clickable(onClick = select))
        RowLine {
            RowCells(row)
            RowButtons(row.id, shown, rowMenu, onEvent)
        }
    }
}

/** The cells of a row; the padding above and below comes from the density of the list. */
@Composable
private fun RowLine(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(
                horizontal = 10.dp,
                vertical = AmTheme.density.listMetrics().vertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
        content = content,
    )
}

@Composable
private fun RowScope.RowCells(row: SessionRow) {
    val metrics = AmTheme.density.listMetrics()
    AmIconTile(row.glyph.icon, row.glyph.tone, MissionControlStrings[row.glyph.key], Modifier.size(metrics.tile))
    Column(modifier = Modifier.weight(weight = 1f)) {
        AmText(row.name, role = TextRole.NAME)
        if (metrics.twoLines) AmText(row.summary.orEmpty(), role = TextRole.CAPTION, color = mutedColor())
    }
    row.project?.let { AmText(MissionControlStrings.format(it), color = mutedColor()) }
    if (row.fromDisk) DiskBadge(row.id)
    row.place?.let { PlaceBadge(row.id, it) }
    AmChip(MissionControlStrings.format(row.lastEvent), tone = row.glyph.tone)
    AmText(MissionControlStrings.format(row.age), color = mutedColor())
}

/**
 * The session was read from a session file only (FR-P9): a neutral chip with the text "z dysku", read with a longer
 * description, so the meaning is not in the colour.
 */
@Composable
private fun DiskBadge(id: SessionId) {
    AmChip(
        label = MissionControlStrings["row.fromDisk"],
        modifier = Modifier.testTag(HistoryTags.rowDisk(id)),
        description = MissionControlStrings["row.fromDisk.description"],
    )
}

/**
 * Where the session runs (AM-310): a neutral chip with the folder icon showing the bare subfolder name or "folder
 * główny", read as "folder: name" / "folder główny"; at most 120 dp wide, a longer name is cut.
 */
@Composable
private fun PlaceBadge(
    id: SessionId,
    place: UiText,
) {
    val description = MissionControlStrings.format(place)
    AmChip(
        label = if (place.key == SessionPlace.SUBFOLDER_KEY) place.args.first() else description,
        modifier = Modifier.testTag(MissionControlTags.rowPlace(id)).widthIn(max = 120.dp),
        icon = IconKind.FOLDER,
        description = description,
    )
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
