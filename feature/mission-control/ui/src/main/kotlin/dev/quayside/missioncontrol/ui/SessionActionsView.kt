package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Stop, Respawn, Remove and "Podgląd" buttons of the details panel, then the "in progress" text or the failure. */
@Composable
internal fun SessionActionsView(
    details: SessionDetails,
    onEvent: (MissionControlEvent) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
        SessionAction.entries.filter { it in details.actions }.forEach { action ->
            ActionButton(
                tagOf(action),
                "action.${action.name.lowercase()}",
            ) { onEvent(MissionControlEvent.Run(action)) }
        }
        ActionButton(
            MissionControlTags.DETAILS_PREVIEW,
            "action.preview",
        ) { onEvent(MissionControlEvent.TogglePreview) }
    }
    details.busy?.let {
        Box(Modifier.testTag(MissionControlTags.DETAILS_BUSY)) {
            Label(MissionControlStrings["action.${it.name.lowercase()}.busy"], Colors.secondary)
        }
    }
    details.failure?.let {
        Column(Modifier.testTag(MissionControlTags.DETAILS_FAILURE)) {
            Label(MissionControlStrings["action.${it.action.name.lowercase()}.failed"], Colors.of(Tone.ERR))
            Label(MissionControlStrings.format(it.reason), Colors.of(Tone.ERR))
        }
    }
}

@Composable
private fun ActionButton(
    tag: String,
    labelKey: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .testTag(tag)
                .focusProperties { canFocus = false }
                .clickable(role = Role.Button, onClick = onClick)
                .background(color = Colors.button)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Label(MissionControlStrings[labelKey])
    }
}

private fun tagOf(action: SessionAction): String =
    when (action) {
        SessionAction.STOP -> MissionControlTags.DETAILS_STOP
        SessionAction.RESPAWN -> MissionControlTags.DETAILS_RESPAWN
        SessionAction.REMOVE -> MissionControlTags.DETAILS_REMOVE
    }
