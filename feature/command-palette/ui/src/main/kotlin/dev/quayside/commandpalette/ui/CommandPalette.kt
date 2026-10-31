package dev.quayside.commandpalette.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.quayside.commandpalette.api.Command

/** The palette over the whole window; shows nothing while closed. */
@Composable
public fun CommandPalette(controller: CommandPaletteController) {
    val view by controller.view.collectAsState()
    if (!view.open) return
    Popup(
        popupPositionProvider = WholeWindow,
        onDismissRequest = { controller.onEvent(PaletteEvent.Dismiss) },
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(color = PaletteColors.scrim)
                    .pointerInput(Unit) { detectTapGestures { controller.onEvent(PaletteEvent.Dismiss) } }
                    .testTag(PaletteTags.ROOT),
            contentAlignment = Alignment.TopCenter,
        ) {
            Panel(view, controller::onEvent)
        }
    }
}

/** The box with the field and the results; swallows taps so that only the scrim dismisses. */
@Composable
private fun Panel(
    view: PaletteView,
    onEvent: (PaletteEvent) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .padding(top = 80.dp)
                .width(width = 560.dp)
                .background(color = PaletteColors.panel, shape = RoundedCornerShape(size = 10.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(all = 12.dp),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        QueryField(onEvent)
        if (view.results.isEmpty()) {
            BasicText(
                text = PaletteStrings["palette.empty"],
                modifier = Modifier.testTag(PaletteTags.EMPTY),
                style = TextStyle(color = PaletteColors.secondaryText, fontSize = 13.sp),
            )
        } else {
            Results(view, onEvent)
        }
    }
}

/** Search field with focus on open; Enter, arrows and Escape are handled before the text field sees them. */
@Composable
private fun QueryField(onEvent: (PaletteEvent) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            onEvent(PaletteEvent.QueryChanged(it))
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = PaletteColors.field)
                .padding(all = 8.dp)
                .focusRequester(focus)
                .paletteKeys(onEvent)
                .testTag(PaletteTags.FIELD),
        singleLine = true,
        textStyle = TextStyle(color = PaletteColors.text, fontSize = 15.sp),
        cursorBrush = SolidColor(PaletteColors.text),
        decorationBox = { inner ->
            Box {
                if (text.isEmpty()) Hint(PaletteStrings["palette.placeholder"])
                inner()
            }
        },
    )
}

@Composable
private fun Hint(text: String) =
    BasicText(text, style = TextStyle(color = PaletteColors.secondaryText, fontSize = 15.sp))

@Composable
private fun Results(
    view: PaletteView,
    onEvent: (PaletteEvent) -> Unit,
) {
    val list = rememberLazyListState()
    LaunchedEffect(view.highlighted) { list.reveal(view.highlighted) }
    LazyColumn(state = list, modifier = Modifier.heightIn(max = 360.dp)) {
        itemsIndexed(view.results, key = { _, command -> command.id.value }) { index, command ->
            val highlighted = index == view.highlighted
            ResultRow(command, highlighted) { onEvent(PaletteEvent.Pick(index)) }
        }
    }
}

/** Title and shortcut in one merged node; selected = highlighted. */
@Composable
private fun ResultRow(
    command: Command,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(PaletteTags.row(command.id))
                .selectable(selected = highlighted, onClick = onClick)
                .background(color = if (highlighted) PaletteColors.highlight else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(command.title, style = TextStyle(color = PaletteColors.text, fontSize = 14.sp), maxLines = 1)
        command.shortcut?.let {
            BasicText(ShortcutLabel.of(it), style = TextStyle(color = PaletteColors.secondaryText, fontSize = 13.sp))
        }
    }
}

/** Scrolls only when [index] is outside the visible rows. */
private suspend fun LazyListState.reveal(index: Int) {
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty() || index < visible.first().index || index > visible.last().index) scrollToItem(index)
}

/** Enter, arrows and Escape go to the palette before the text field sees them. */
private fun Modifier.paletteKeys(onEvent: (PaletteEvent) -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        val action = if (event.type == KeyEventType.KeyDown) KEY_ACTIONS[event.key] else null
        action?.let(onEvent) != null
    }

private val KEY_ACTIONS: Map<Key, PaletteEvent> =
    mapOf(
        Key.Enter to PaletteEvent.Confirm,
        Key.NumPadEnter to PaletteEvent.Confirm,
        Key.DirectionUp to PaletteEvent.MoveUp,
        Key.DirectionDown to PaletteEvent.MoveDown,
        Key.Escape to PaletteEvent.Dismiss,
    )

/** Placeholder colors until `core:design-system` exists (AM-802). */
private object PaletteColors {
    val scrim = Color(color = 0x99000000)
    val panel = Color(color = 0xFF2C2C2E)
    val field = Color(color = 0xFF48484A)
    val highlight = Color(color = 0xFF3A3A3C)
    val text = Color(color = 0xFFF2F2F7)
    val secondaryText = Color(color = 0xFF8E8E93)
}

/** Places the palette layer over the whole window, whatever the anchor. */
private object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}
