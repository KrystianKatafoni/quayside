package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * "? skróty klawiszowe" toggle with the cheat sheet above it (closed at start); the toggle never takes focus, so a
 * click does not turn `space` and `⏎` into a second click. Put it at the bottom of the left column (AM-816).
 */
@Composable
public fun ShortcutsHelp(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(value = false) }
    Column(modifier = modifier) {
        if (open) ShortcutsPanel()
        ShortcutsToggle(open) { open = !open }
    }
}
