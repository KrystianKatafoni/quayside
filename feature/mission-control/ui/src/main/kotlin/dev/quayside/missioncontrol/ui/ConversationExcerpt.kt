package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.ButtonOptions
import dev.quayside.core.designsystem.TextRole
import dev.quayside.sessions.api.SessionMessage

/** How many of the newest messages the details panel shows, and how many lines of each. */
private const val EXCERPT_MESSAGES = 3
private const val EXCERPT_LINES = 3

/**
 * "OSTATNIE WIADOMOŚCI" in the details panel of a disk session (FR-P9, AM-315): the last three messages of
 * [conversation], three lines each; nothing while there are none.
 */
@Composable
internal fun ConversationExcerpt(conversation: ConversationUi?) {
    val last = conversation?.messages?.takeLast(EXCERPT_MESSAGES).orEmpty()
    if (last.isEmpty()) return
    Column(
        modifier = Modifier.testTag(MissionControlTags.DETAILS_MESSAGES),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        AmText(MissionControlStrings["details.messages"], role = TextRole.LABEL, color = mutedColor())
        last.forEach { ExcerptRow(it) }
    }
}

@Composable
private fun ExcerptRow(message: SessionMessage) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
        AmText(
            MissionControlStrings[message.role.labelKey()],
            modifier = Modifier.width(width = 48.dp),
            role = TextRole.CAPTION,
            color = mutedColor(),
        )
        AmText(message.text, modifier = Modifier.weight(weight = 1f), maxLines = EXCERPT_LINES)
    }
}

/** "Pokaż historię": opens the preview, like Space. Never takes focus (Space and Enter belong to the window). */
@Composable
internal fun HistoryButton(onEvent: (MissionControlEvent) -> Unit) {
    AmButton(
        MissionControlStrings["details.history"],
        { onEvent(MissionControlEvent.TogglePreview) },
        Modifier.testTag(MissionControlTags.DETAILS_HISTORY),
        ButtonOptions(focusable = false),
    )
}
