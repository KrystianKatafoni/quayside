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

/** One thing the user can do with the selected session; the details panel and the row menu draw the same list. */
internal data class SessionControl(
    val id: String,
    val labelKey: String,
    val event: MissionControlEvent,
)

/** "Attach" and "Odpowiedz" (AM-511) when they apply now. */
internal fun SessionDetails.openControls(): List<SessionControl> =
    listOfNotNull(
        SessionControl("attach", "action.attach", MissionControlEvent.Attach).takeIf { canAttach },
        SessionControl("reply", "action.reply", MissionControlEvent.Reply).takeIf { canReply },
    )

/** Everything the row menu offers: [openControls], then [otherControls]. */
internal fun SessionDetails.allControls(): List<SessionControl> = openControls() + otherControls()

/** Stop, Respawn and Remove when they apply now, then the always available "Podgląd". */
internal fun SessionDetails.otherControls(): List<SessionControl> =
    SessionAction.entries.filter { it in actions }.map {
        SessionControl(it.name.lowercase(), "action.${it.name.lowercase()}", MissionControlEvent.Run(it))
    } + SessionControl("preview", "action.preview", MissionControlEvent.TogglePreview)

/** Attach and reply buttons, then stop, respawn, remove and "Podgląd", then the "in progress" text or the failure. */
@Composable
internal fun SessionActionsView(
    details: SessionDetails,
    onEvent: (MissionControlEvent) -> Unit,
) {
    listOf(details.openControls(), details.otherControls()).filter { it.isNotEmpty() }.forEach { controls ->
        Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
            controls.forEach { control ->
                ActionButton(MissionControlTags.details(control.id), control.labelKey) { onEvent(control.event) }
            }
        }
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
