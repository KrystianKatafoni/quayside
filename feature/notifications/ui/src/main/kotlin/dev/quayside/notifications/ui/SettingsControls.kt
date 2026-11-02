package dev.quayside.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val controlShape = RoundedCornerShape(size = 4.dp)

/** Draws a frame round the control while it has the focus, so the Tab order is visible (NFR-6). */
@Composable
internal fun Modifier.focusFrame(): Modifier {
    var focused by remember { mutableStateOf(value = false) }
    val color = if (focused) SettingsColors.accent else Color.Transparent
    return onFocusChanged { focused = it.isFocused }.border(width = 2.dp, color = color, shape = controlShape)
}

// The controls of the settings dialog, copied from `terminal:ui` (a `ui` module must not depend on another `ui`);
// all of them are focusable and sit in the tab order of the screen.

/** One line of text in the colours of the dialog. */
@Composable
internal fun Label(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SettingsColors.text,
    size: Int = 13,
) {
    BasicText(text, modifier, TextStyle(color = color, fontSize = size.sp), maxLines = 1)
}

/** A label and its [control]; [invalid] shows its message under the row. */
@Composable
internal fun SettingRow(
    labelKey: String,
    invalid: InvalidField?,
    control: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(SettingsStrings[labelKey], Modifier.width(width = 200.dp))
            control()
        }
        invalid?.let {
            Label(
                SettingsStrings.of(it),
                Modifier.testTag(SettingsTags.invalid(it.field)),
                SettingsColors.error,
                size = 12,
            )
        }
    }
}

/** A text field; Tab leaves it for the next control. */
@Composable
internal fun TextSetting(
    tag: String,
    value: String,
    onChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier =
            Modifier
                .width(width = 260.dp)
                .testTag(tag)
                .background(color = SettingsColors.field, shape = controlShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        singleLine = true,
        textStyle = TextStyle(color = SettingsColors.text, fontSize = 13.sp),
        cursorBrush = SolidColor(SettingsColors.text),
    )
}

/** One of [labels]; ← and → step through them (wrapping), a click steps forward and takes the focus. */
@Composable
internal fun ChoiceField(
    tag: String,
    labels: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val step = { by: Int -> onSelect(Math.floorMod(selected + by, labels.size)) }
    Row(
        modifier
            .width(width = 260.dp)
            .testTag(tag)
            .onPreviewKeyEvent { event ->
                val by = arrowStep(event.key).takeIf { event.type == KeyEventType.KeyDown }
                by?.let(step)
                by != null
            }.focusFrame()
            .background(color = SettingsColors.field, shape = controlShape)
            .clickable(role = Role.DropdownList) { step(1) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Label("‹", Modifier.clearAndSetSemantics {}, SettingsColors.secondary)
        Label(labels[selected], Modifier.weight(weight = 1f))
        Label("›", Modifier.clearAndSetSemantics {}, SettingsColors.secondary)
    }
}

private fun arrowStep(key: Key): Int? =
    when (key) {
        Key.DirectionRight -> 1
        Key.DirectionLeft -> -1
        else -> null
    }

/** The box of a checkbox: a tick when [on]. */
@Composable
internal fun CheckMark(on: Boolean) {
    Box(
        Modifier
            .width(width = 18.dp)
            .background(color = if (on) SettingsColors.accent else SettingsColors.field, shape = controlShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Label(text = if (on) "✓" else " ")
    }
}

/** A checkbox with its label; Space toggles it. [modifier] comes first, so a caller can add a focus requester. */
@Composable
internal fun ToggleSetting(
    tag: String,
    labelKey: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            modifier
                .testTag(
                    tag,
                ).focusFrame()
                .toggleable(value = on, role = Role.Checkbox, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckMark(on)
        Label(SettingsStrings[labelKey])
    }
}

@Composable
internal fun DialogButton(
    tag: String,
    textKey: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .testTag(tag)
            .focusFrame()
            .background(color = SettingsColors.field, shape = controlShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Label(SettingsStrings[textKey])
    }
}

/** Colours of the dialog; the values match the settings dialog of the terminal. */
internal object SettingsColors {
    val panel = Color(color = 0xFF2C2C2E)
    val field = Color(color = 0xFF3A3A3C)
    val accent = Color(color = 0xFF0A84FF)
    val text = Color(color = 0xFFF2F2F7)
    val secondary = Color(color = 0xFFAEAEB2)
    val error = Color(color = 0xFFFF7B72)
}
