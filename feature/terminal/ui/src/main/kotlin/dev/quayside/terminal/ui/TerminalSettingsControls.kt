package dev.quayside.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quayside.terminal.api.TerminalSettingsField

private val controlShape = RoundedCornerShape(size = 4.dp)

// The controls of the settings dialog (AM-508); all of them are focusable and sit in the tab order of the screen.

/** A label and its [control]; [invalid] shows its message under the row. */
@Composable
internal fun SettingRow(
    labelKey: String,
    invalid: TerminalSettingsField?,
    control: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(TerminalStrings[labelKey], Modifier.width(width = 200.dp))
            control()
        }
        invalid?.let {
            Label(
                TerminalStrings.of(it),
                Modifier.testTag(TerminalTags.settingsInvalid(it)),
                TerminalColors.error,
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
                .background(color = TerminalColors.selection, shape = controlShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        singleLine = true,
        textStyle = TextStyle(color = TerminalColors.text, fontSize = 13.sp),
        cursorBrush = SolidColor(TerminalColors.text),
    )
}

/**
 * Picks one of [labels]: a click, Enter or Space goes to the next one, the arrow keys to the next and the previous.
 * [selected] is an index into [labels].
 */
@Composable
internal fun ChoiceField(
    tag: String,
    labels: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val step = { by: Int -> onSelect(Math.floorMod(selected + by, labels.size)) }
    Box(
        modifier
            .width(width = 260.dp)
            .testTag(tag)
            .onPreviewKeyEvent { event ->
                val by = arrowStep(event.key).takeIf { event.type == KeyEventType.KeyDown }
                by?.let(step)
                by != null
            }.background(color = TerminalColors.selection, shape = controlShape)
            .clickable(role = Role.DropdownList) { step(1) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Label("‹  ${labels[selected]}  ›")
    }
}

private fun arrowStep(key: Key): Int? =
    when (key) {
        Key.DirectionRight -> 1
        Key.DirectionLeft -> -1
        else -> null
    }

/** A checkbox with its label; Space toggles it. */
@Composable
internal fun ToggleSetting(
    tag: String,
    labelKey: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.testTag(tag).toggleable(value = on, role = Role.Checkbox, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(width = 18.dp)
                .background(color = if (on) TerminalColors.accent else TerminalColors.selection, shape = controlShape),
            contentAlignment = Alignment.Center,
        ) {
            Label(text = if (on) "✓" else " ")
        }
        Label(TerminalStrings[labelKey])
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
            .background(color = TerminalColors.selection, shape = controlShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Label(TerminalStrings[textKey])
    }
}
