package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmDropdown
import dev.quayside.core.designsystem.AmDropdownItems
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

/**
 * An [AmDropdown] of the dialog as wide as its column, tagged [tag] and named by the setting `labelKey` for a screen
 * reader; [enabled] false (a start runs) keeps the list closed.
 */
@Composable
internal fun Dropdown(
    tag: String,
    labelKey: String,
    items: AmDropdownItems,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    AmDropdown(items, MissionControlStrings[labelKey], onSelect, Modifier.fillMaxWidth().testTag(tag), enabled)
}

/** The name of a field for a screen reader (its label), put on the control. */
internal fun Modifier.described(label: String): Modifier = semantics { contentDescription = label }
