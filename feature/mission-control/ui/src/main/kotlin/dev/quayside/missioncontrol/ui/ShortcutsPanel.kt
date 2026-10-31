package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The "Skróty" toggle of the mode bar (AM-511, ADR 0015). Never takes focus, so a click does not turn `space` and
 * `⏎` into a second click.
 */
@Composable
internal fun ShortcutsToggle(
    open: Boolean,
    onToggle: () -> Unit,
) {
    val color = if (open) Colors.accent else Colors.secondary
    Box(
        modifier =
            Modifier
                .testTag(MissionControlTags.SHORTCUTS_TOGGLE)
                .focusProperties { canFocus = false }
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(horizontal = 6.dp),
    ) {
        Label(MissionControlStrings["shortcuts.toggle"], color)
    }
}

/** The cheat sheet of the keyboard shortcuts, drawn above the mode bar while it is open. */
@Composable
internal fun ShortcutsPanel() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = Colors.panel)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag(MissionControlTags.SHORTCUTS),
        verticalArrangement = Arrangement.spacedBy(space = 2.dp),
    ) {
        Label(MissionControlStrings["shortcuts.title"], size = 14)
        SHORTCUT_KEYS.forEach { Label(MissionControlStrings["shortcuts.$it"], Colors.secondary, mono = true) }
    }
}

private val SHORTCUT_KEYS = listOf("navigate", "attach", "reply", "stop", "preview", "project", "palette")
