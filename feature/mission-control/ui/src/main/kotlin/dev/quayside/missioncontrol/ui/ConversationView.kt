package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.ButtonOptions
import dev.quayside.core.designsystem.TextRole
import dev.quayside.sessions.api.MessageRole
import dev.quayside.sessions.api.SessionMessage

/**
 * The messages of a disk session in the preview (FR-P9, AM-315), oldest first, from the top: "Wczytaj starsze" is
 * the first thing while the file has earlier messages, and the older page is added right under it. The author is a
 * text ("Ty", "Claude"), not only a colour; the text is selectable.
 */
@Composable
internal fun ConversationView(
    conversation: ConversationUi?,
    onEvent: (MissionControlEvent) -> Unit,
) {
    if (conversation == null || (conversation.loading && conversation.messages.isEmpty())) {
        AmText(MissionControlStrings["conversation.loading"], color = mutedColor())
        return
    }
    SelectionContainer {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag(MissionControlTags.PREVIEW_CONVERSATION)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(space = 6.dp),
        ) {
            if (conversation.older != null) LoadOlderButton(conversation.loading, onEvent)
            conversation.failure?.let { Failure(it) }
            if (conversation.messages.isEmpty() && conversation.failure == null) {
                AmText(MissionControlStrings["conversation.empty"], color = mutedColor())
            }
            conversation.messages.forEach { MessageRow(it) }
        }
    }
}

/** Never takes focus: Space and Enter belong to the window. Disabled while a page is read. */
@Composable
private fun LoadOlderButton(
    loading: Boolean,
    onEvent: (MissionControlEvent) -> Unit,
) {
    AmButton(
        MissionControlStrings["conversation.loadOlder"],
        { onEvent(MissionControlEvent.LoadOlder) },
        Modifier.testTag(MissionControlTags.PREVIEW_LOAD_OLDER),
        ButtonOptions(enabled = !loading, focusable = false),
    )
}

@Composable
private fun Failure(reason: UiText) {
    Column {
        AmText(MissionControlStrings["conversation.failed"], color = Tone.ERR.tint())
        AmText(MissionControlStrings.format(reason), color = Tone.ERR.tint())
    }
}

@Composable
private fun MessageRow(message: SessionMessage) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        AmText(
            MissionControlStrings[message.role.labelKey()],
            modifier = Modifier.width(width = ROLE_WIDTH),
            role = TextRole.LABEL,
            color = mutedColor(),
        )
        Column(modifier = Modifier.weight(weight = 1f)) {
            AmText(message.text, maxLines = Int.MAX_VALUE)
            if (message.truncated) {
                AmText(MissionControlStrings["conversation.truncated"], role = TextRole.CAPTION, color = mutedColor())
            }
        }
    }
}

internal fun MessageRole.labelKey(): String =
    when (this) {
        MessageRole.USER -> "conversation.role.user"
        MessageRole.ASSISTANT -> "conversation.role.assistant"
    }

private val ROLE_WIDTH = 56.dp
