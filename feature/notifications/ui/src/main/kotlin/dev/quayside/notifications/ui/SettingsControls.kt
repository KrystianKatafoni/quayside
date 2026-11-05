package dev.quayside.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmDropdown
import dev.quayside.core.designsystem.AmDropdownItems
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.amFocusFrame
import dev.quayside.core.designsystem.toColor

// The controls of the settings dialog, copied from `terminal:ui` (a `ui` module must not depend on another `ui`);
// all of them are focusable and sit in the tab order of the screen.

/**
 * A [label] and its [control]; [invalid] shows its message under the row. The control gets a modifier that names it
 * for the screen reader with the [label] (NFR-6, S3).
 */
@Composable
internal fun SettingRow(
    label: String,
    invalid: InvalidField?,
    control: @Composable (Modifier) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AmText(label, Modifier.width(width = 200.dp))
            control(Modifier.semantics { contentDescription = label })
        }
        invalid?.let {
            AmText(
                SettingsStrings.of(it),
                Modifier.testTag(SettingsTags.invalid(it.field)),
                TextRole.CAPTION,
                errColor(),
            )
        }
    }
}

/** A text field; Tab leaves it for the next control. */
@Composable
internal fun TextSetting(
    tag: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier =
            modifier
                .width(width = 260.dp)
                .testTag(tag)
                .background(
                    color = raisedColor(),
                    shape = AmTheme.shape.corner,
                ).padding(horizontal = 8.dp, vertical = 4.dp),
        singleLine = true,
        textStyle = AmTheme.typography.style(TextRole.BODY).copy(color = textColor()),
        cursorBrush = SolidColor(textColor()),
    )
}

/** An [AmDropdown] as wide as a text field, named [label] for the screen reader; the list holds the choices. */
@Composable
internal fun Dropdown(
    tag: String,
    label: String,
    items: AmDropdownItems,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    AmDropdown(items, label, onSelect, modifier.width(width = 260.dp).testTag(tag))
}

/** The box of a checkbox: a tick when [on]. */
@Composable
internal fun CheckMark(on: Boolean) {
    val colors = AmTheme.colors
    val (fill, tick) =
        if (on) {
            colors.content.accent to colors.content.accentInk
        } else {
            colors.surfaces.raised to
                colors.content.text
        }
    Box(
        Modifier
            .width(width = 18.dp)
            .background(color = fill.toColor(), shape = AmTheme.shape.corner)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        AmText(text = if (on) "✓" else " ", color = tick.toColor())
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
                .testTag(tag)
                .amFocusFrame()
                .toggleable(value = on, role = Role.Checkbox, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckMark(on)
        AmText(SettingsStrings[labelKey])
    }
}

@Composable
internal fun DialogButton(
    tag: String,
    textKey: String,
    description: String? = null,
    onClick: () -> Unit,
) {
    AmButton(
        label = SettingsStrings[textKey],
        onClick = onClick,
        modifier = Modifier.testTag(tag).semantics { description?.let { contentDescription = it } },
    )
}
