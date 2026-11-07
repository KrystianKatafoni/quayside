package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmKeyCap
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.TextRole

/**
 * Hints under the session list: the key caps of [LocalKeyCaps] with what they do (move, attach, reply, stop, preview).
 * The key texts come from `app`; nothing is drawn without key caps.
 */
@Composable
internal fun KeyHintsBar() {
    val caps = LocalKeyCaps.current ?: return
    Row(
        modifier = Modifier.fillMaxWidth().testTag(MissionControlTags.KEY_HINTS),
        horizontalArrangement = Arrangement.spacedBy(space = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Hint(listOf(caps.next, caps.previous), "hints.move")
        Hint(listOf(caps.attach), "hints.attach")
        Hint(listOf(caps.reply), "hints.reply")
        Hint(listOf(caps.stop), "hints.stop")
        Hint(listOf(caps.preview), "hints.preview")
    }
}

@Composable
private fun Hint(
    keys: List<String>,
    key: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        keys.forEach { AmKeyCap(it) }
        AmText(MissionControlStrings[key], role = TextRole.CAPTION, color = mutedColor())
    }
}
