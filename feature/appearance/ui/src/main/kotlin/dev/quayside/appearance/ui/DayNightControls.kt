package dev.quayside.appearance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.amFocusFrame
import dev.quayside.core.designsystem.toColor

/**
 * The switch "Dzień i noc z macOS" and, while it is on, the segment "Dzień | Noc" that says which of the two themes
 * the list below edits (FR-A5).
 */
@Composable
internal fun DayNightControls(
    draft: AppearanceDraft,
    onIntent: (AppearanceIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(space = 6.dp)) {
        DayNightSwitch(draft.settings.dayNight.enabled) { onIntent(AppearanceIntent.ToggleDayNight) }
        if (draft.settings.dayNight.enabled) {
            Box(Modifier.testTag(AppearanceTags.SLOT_SEGMENT)) {
                Segments(
                    ThemeSlot.entries,
                    draft.slot,
                    { AppearanceStrings["slot.${it.name}"] },
                    { onIntent(AppearanceIntent.SelectSlot(it)) },
                ) { Modifier.testTag(AppearanceTags.slot(it)) }
            }
        }
    }
}

/** A focusable switch (Role.Switch, Space and Enter toggle it); sel background and a check mark while on. */
@Composable
private fun DayNightSwitch(
    on: Boolean,
    onToggle: () -> Unit,
) {
    val colors = AmTheme.colors
    val background = if (on) colors.surfaces.sel.toColor() else Color.Transparent
    Row(
        modifier =
            Modifier
                .testTag(AppearanceTags.DAY_NIGHT_SWITCH)
                .amFocusFrame()
                .clip(AmTheme.shape.corner)
                .background(background)
                .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() })
                .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmText(AppearanceStrings["dayNight.switch"], color = colors.content.text.toColor())
        if (on) AmText("✓", color = colors.content.text.toColor())
    }
}
