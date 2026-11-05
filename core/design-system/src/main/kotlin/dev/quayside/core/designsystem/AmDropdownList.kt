package dev.quayside.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

private const val MAX_ROWS = 8
private val LIST_GAP = 2.dp

/** The list under (or over) the field: a focusable popup, so only the list gets keys while it is open. */
@Composable
internal fun DropdownList(
    items: AmDropdownItems,
    fieldWidth: Dp,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val gap = with(LocalDensity.current) { LIST_GAP.roundToPx() }
    var rowHeight by remember { mutableIntStateOf(0) }
    Popup(
        popupPositionProvider = remember(gap) { BelowOrAbove(gap) },
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true),
    ) {
        // Hidden until the first row is measured: before that the list has no row limit and would jump.
        val shown = Modifier.alpha(if (rowHeight > 0) 1f else 0f)
        AmPanel(
            modifier = shown.amModalLayer().widthIn(min = fieldWidth).width(IntrinsicSize.Max),
            raised = true,
        ) {
            OptionRows(items, rowHeight, { rowHeight = it }, onPick, onClose)
        }
    }
}

/** The focus of the open list: one requester per row and the row that has the focus now. */
@Stable
private class ListFocus(
    count: Int,
    val start: Int,
) {
    private val requesters = List(count) { FocusRequester() }
    private var at by mutableIntStateOf(start)

    fun requester(index: Int): FocusRequester = requesters[index]

    fun took(index: Int) {
        at = index
    }

    fun moveBy(by: Int) {
        requesters[dropdownStep(at, by, requesters.size)].requestFocus()
    }

    fun focusStart() {
        requesters.getOrNull(start)?.requestFocus()
    }
}

/** At most [MAX_ROWS] rows of [rowHeight] px; unlimited until the first row is measured. */
@Composable
private fun Modifier.maxRows(rowHeight: Int): Modifier =
    if (rowHeight > 0) heightIn(max = with(LocalDensity.current) { (rowHeight * MAX_ROWS).toDp() }) else this

/**
 * The rows in one scrolling `selectableGroup`, at most [MAX_ROWS] high (measured from the first row). The focus
 * starts on the selected row; Up and Down move it by [dropdownStep], Escape and Tab close, and Enter, Space and a
 * click are handled by the rows themselves. A Meta, Ctrl or Alt key is not ours.
 */
@Composable
private fun OptionRows(
    items: AmDropdownItems,
    rowHeight: Int,
    onRowHeight: (Int) -> Unit,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val count = items.labels.size
    val focus = remember(count) { ListFocus(count, openAt(items.selected, count)) }
    LaunchedEffect(focus) { focus.focusStart() }
    Column(
        modifier =
            Modifier
                .testTag(AmDropdownTags.LIST)
                .maxRows(rowHeight)
                .onPreviewKeyEvent { event -> listKey(event, onClose, focus::moveBy) }
                .verticalScroll(rememberScrollState())
                .selectableGroup(),
    ) {
        items.labels.forEachIndexed { index, text ->
            val measure = if (index == 0) Modifier.onSizeChanged { onRowHeight(it.height) } else Modifier
            val tagged = measure.testTag(AmDropdownTags.option(index)).focusRequester(focus.requester(index))
            val pick = {
                onPick(index)
                onClose()
            }
            OptionRow(text, index == items.selected, tagged, pick) { focus.took(index) }
        }
    }
}

/** One row; [onFocus] runs when it takes the focus. */
@Composable
private fun OptionRow(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onFocus: () -> Unit,
) {
    AmListRow(
        selected = selected,
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        AmText(text, Modifier.weight(1f))
    }
}

/** Handles Up, Down, Escape and Tab of the open list; true when the key is consumed. */
private fun listKey(
    event: KeyEvent,
    onClose: () -> Unit,
    move: (by: Int) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown || event.hasShortcutModifier()) return false
    return when (event.key) {
        Key.DirectionDown -> true.also { move(1) }
        Key.DirectionUp -> true.also { move(-1) }
        Key.Escape, Key.Tab -> true.also { onClose() }
        else -> false
    }
}

/** Puts the list by [dropdownPosition]. */
private class BelowOrAbove(
    private val gap: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = dropdownPosition(anchorBounds, windowSize, popupContentSize, gap)
}
