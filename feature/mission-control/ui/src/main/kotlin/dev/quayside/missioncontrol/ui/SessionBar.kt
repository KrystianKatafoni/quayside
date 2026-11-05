package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmIconButton
import dev.quayside.core.designsystem.AmIconTile
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.ButtonKind
import dev.quayside.core.designsystem.ButtonOptions
import dev.quayside.core.designsystem.IconButtonOptions
import dev.quayside.core.designsystem.IconButtonStyle
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.domain.SessionId

/**
 * Bar above the terminal (B): tile, name, state and age of [session], then Odpowiedz (PRIMARY, when it waits),
 * Respawn (SECONDARY, inactive) and Stop (AmIconButton DANGER, "Zatrzymaj sesję", active). Draws nothing unless
 * [session] is the selected visible session (its details exist). Buttons never take focus.
 */
@Composable
public fun SessionBar(
    controller: MissionControlController,
    session: SessionId?,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val screen = state.screen
    val details = screen.shownDetails()?.takeIf { it.id == session }
    if (details == null) return
    val age = screen.shownRows().firstOrNull { it.id == details.id }?.age
    val onEvent: (MissionControlEvent) -> Unit = { controller.onEvent(it) }
    val status = statusLine(details, age)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(MissionControlTags.SESSION_BAR)
                // The bar says "name, state · age" as one text; the parts below are hidden from the reader.
                .semantics { text = AnnotatedString("${details.name}, $status") }
                .background(panelColor())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarTitle(details, status, Modifier.weight(weight = 1f))
        BarButtons(details, onReply, onEvent)
    }
}

private fun Screen.shownDetails(): SessionDetails? =
    when (this) {
        is Screen.Project -> details
        is Screen.Global -> details
        else -> null
    }

private fun Screen.shownRows(): List<SessionRow> =
    when (this) {
        is Screen.Project -> rows
        is Screen.Global -> rows
        else -> emptyList()
    }

private fun statusLine(
    details: SessionDetails,
    age: UiText?,
): String {
    val state = MissionControlStrings[details.glyph.key]
    return if (age == null) state else "$state · ${MissionControlStrings.format(age)}"
}

@Composable
private fun BarTitle(
    details: SessionDetails,
    status: String,
    modifier: Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmIconTile(
            details.glyph.icon,
            details.glyph.tone,
            MissionControlStrings[details.glyph.key],
            Modifier.size(BAR_TILE).testTag(MissionControlTags.SESSION_BAR_TILE),
        )
        Column(modifier = Modifier.clearAndSetSemantics {}) {
            AmText(details.name, role = TextRole.CODE)
            AmText(status, role = TextRole.CAPTION, color = mutedColor())
        }
    }
}

@Composable
private fun BarButtons(
    details: SessionDetails,
    onReply: () -> Unit,
    onEvent: (MissionControlEvent) -> Unit,
) {
    if (details.canReply) {
        AmButton(
            MissionControlStrings["action.reply"],
            onReply,
            Modifier.testTag(MissionControlTags.SESSION_BAR_REPLY),
            ButtonOptions(kind = ButtonKind.PRIMARY, focusable = false, icon = IconKind.REPLY),
        )
    }
    AmButton(
        MissionControlStrings["action.respawn"],
        { onEvent(MissionControlEvent.Run(SessionAction.RESPAWN)) },
        Modifier.testTag(MissionControlTags.SESSION_BAR_RESPAWN),
        ButtonOptions(focusable = false, enabled = SessionAction.RESPAWN in details.actions, icon = IconKind.RESPAWN),
    )
    AmIconButton(
        IconKind.STOP,
        MissionControlStrings["bar.stop"],
        { onEvent(MissionControlEvent.Run(SessionAction.STOP)) },
        Modifier.testTag(MissionControlTags.SESSION_BAR_STOP),
        IconButtonOptions(
            enabled = SessionAction.STOP in details.actions,
            focusable = false,
            style = IconButtonStyle.DANGER,
        ),
    )
}

private val BAR_TILE = 34.dp
