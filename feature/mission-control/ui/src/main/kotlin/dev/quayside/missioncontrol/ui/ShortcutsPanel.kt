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
import dev.quayside.core.designsystem.AmModeBadge
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.TextRole

/**
 * The "Skróty" toggle of the mode bar (AM-511, ADR 0015). Never takes focus, so a click does not turn `space` and
 * `⏎` into a second click.
 */
@Composable
internal fun ShortcutsToggle(
    open: Boolean,
    onToggle: () -> Unit,
) {
    val label = MissionControlStrings["shortcuts.toggle"]
    Box(
        modifier =
            Modifier
                .testTag(MissionControlTags.SHORTCUTS_TOGGLE)
                .focusProperties { canFocus = false }
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(horizontal = 6.dp),
    ) {
        // Open = a badge (accentInk on accent): accent as text colour is not covered by the contrast audit.
        if (open) AmModeBadge(label) else AmText(label, role = TextRole.CAPTION)
    }
}

/** The cheat sheet of the keyboard shortcuts, drawn above the mode bar while it is open. */
@Composable
internal fun ShortcutsPanel() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = panelColor())
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag(MissionControlTags.SHORTCUTS),
        verticalArrangement = Arrangement.spacedBy(space = 2.dp),
    ) {
        AmText(MissionControlStrings["shortcuts.title"], role = TextRole.HEADING)
        SHORTCUT_KEYS.forEach {
            AmText(
                MissionControlStrings["shortcuts.$it"],
                role = TextRole.CODE,
                color = mutedColor(),
            )
        }
    }
}

private val SHORTCUT_KEYS = listOf("navigate", "attach", "reply", "stop", "preview", "project", "palette")
