package dev.agentmanager.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Why a terminal did not open, with an x that dismisses the message. */
@Composable
internal fun ErrorBar(
    failure: OpenFailure,
    onDismiss: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(TerminalTags.ERROR)
                .background(color = TerminalColors.warning)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(text = TerminalStrings.of(failure), modifier = Modifier.weight(weight = 1f))
        GlyphButton(TerminalTags.ERROR_DISMISS, glyph = "×", onDismiss)
    }
}
