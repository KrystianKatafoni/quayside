package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
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
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.ButtonOptions
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.amFocusFrame

private const val MAX_PROMPT_LINES = 10

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
                .amFocusFrame()
                .onPreviewKeyEvent { multiline && focus.moveOnTab(it) }
                .background(
                    color = raisedColor(),
                    shape = AmTheme.shape.corner,
                ).padding(horizontal = 8.dp, vertical = 4.dp),
        enabled = enabled,
        singleLine = !multiline,
        minLines = minLines,
        maxLines = if (multiline) MAX_PROMPT_LINES else 1,
        textStyle = AmTheme.typography.style(TextRole.BODY).copy(color = textColor()),
        cursorBrush = SolidColor(textColor()),
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
            .amFocusFrame()
            .semantics { stateDescription = shown }
            .onPreviewKeyEvent { event ->
                val by = arrowStep(event.key).takeIf { enabled && event.type == KeyEventType.KeyDown }
                by?.let { onStep(it, true) }
                by != null
            }.background(
                color = raisedColor(),
                shape = AmTheme.shape.corner,
            ).clickable(enabled = enabled, role = Role.DropdownList) { onStep(1, false) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        AmText("‹  $shown  ›")
    }
}

private fun arrowStep(key: Key): Int? =
    when (key) {
        Key.DirectionRight -> 1
        Key.DirectionLeft -> -1
        else -> null
    }

/**
 * A button of the dialog; disabled while a start runs. The merging semantics node sits before the focus frame of the
 * button, so that the bounds of the tagged node include the frame (a pixel check at the edge sees it).
 */
@Composable
internal fun DialogButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AmButton(label, onClick, modifier.semantics(mergeDescendants = true) {}, ButtonOptions(enabled = enabled))
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
