package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val controlShape = RoundedCornerShape(size = 4.dp)

private const val MAX_PROMPT_LINES = 10

/**
 * Draws a frame of [NewAgentColors.focusBorder] around the control while it has the focus (NFR-6). Put it before the
 * modifier that makes the control focusable.
 */
@Composable
internal fun Modifier.focusFrame(): Modifier {
    var focused by remember { mutableStateOf(value = false) }
    val color = if (focused) NewAgentColors.focusBorder else Color.Transparent
    return onFocusChanged { focused = it.isFocused }.border(width = 2.dp, color = color, shape = controlShape)
}

/**
 * A text field. With [minLines] above 1 it is multiline: Enter adds a line, and Tab and Shift+Tab move the focus
 * instead of adding a tab character.
 */
@Composable
internal fun TextBox(
    value: String,
    enabled: Boolean,
    minLines: Int,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    val multiline = minLines > 1
    val focus = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier =
            modifier
                .fillMaxWidth()
                .focusFrame()
                .onPreviewKeyEvent { multiline && focus.moveOnTab(it) }
                .background(color = NewAgentColors.field, shape = controlShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        enabled = enabled,
        singleLine = !multiline,
        minLines = minLines,
        maxLines = if (multiline) MAX_PROMPT_LINES else 1,
        textStyle = TextStyle(color = NewAgentColors.primary, fontSize = 13.sp),
        cursorBrush = SolidColor(NewAgentColors.primary),
    )
}

private fun FocusManager.moveOnTab(event: KeyEvent): Boolean {
    val tab = event.key == Key.Tab
    if (tab && event.type == KeyEventType.KeyDown) {
        moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
    }
    return tab
}

/**
 * Shows [shown], announced as its state. A click, Enter or Space asks for the next choice (`onStep(1, false)`); the
 * arrow keys for the next and the previous one (`arrow` true). Does nothing while it is not [enabled]. Copy of
 * `ChoiceField` of `terminal:ui` (no shared module).
 */
@Composable
internal fun ChoiceField(
    shown: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onStep: (by: Int, arrow: Boolean) -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .focusFrame()
            .semantics { stateDescription = shown }
            .onPreviewKeyEvent { event ->
                val by = arrowStep(event.key).takeIf { enabled && event.type == KeyEventType.KeyDown }
                by?.let { onStep(it, true) }
                by != null
            }.background(color = NewAgentColors.field, shape = controlShape)
            .clickable(enabled = enabled, role = Role.DropdownList) { onStep(1, false) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Label("‹  $shown  ›", NewAgentColors.primary)
    }
}

private fun arrowStep(key: Key): Int? =
    when (key) {
        Key.DirectionRight -> 1
        Key.DirectionLeft -> -1
        else -> null
    }

/** A button of the dialog; disabled while a start runs. */
@Composable
internal fun DialogButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .focusFrame()
            .background(color = NewAgentColors.button, shape = controlShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Label(label, NewAgentColors.primary)
    }
}

/** The name of a field for a screen reader (its label), put on the control. */
internal fun Modifier.described(label: String): Modifier = semantics { contentDescription = label }

/**
 * The element after [current] (before it for a negative [by]) in this list; none yet: first or last. Past the ends it
 * wraps, or with [wrap] false stays on the end.
 */
internal fun <T> List<T>.stepped(
    current: T?,
    by: Int,
    wrap: Boolean = true,
): T {
    val at = indexOf(current)
    val next = if (wrap) Math.floorMod(at + by, size) else (at + by).coerceIn(0, lastIndex)
    return get(if (at < 0) (if (by > 0) 0 else lastIndex) else next)
}
