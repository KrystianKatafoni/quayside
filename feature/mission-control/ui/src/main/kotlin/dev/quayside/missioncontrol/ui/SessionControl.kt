package dev.quayside.missioncontrol.ui

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
