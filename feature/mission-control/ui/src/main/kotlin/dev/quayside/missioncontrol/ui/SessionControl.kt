package dev.quayside.missioncontrol.ui

import dev.quayside.core.designsystem.ButtonKind
import dev.quayside.core.designsystem.IconKind

/** One thing the user can do with the selected session; the details panel and the row menu draw the same list. */
internal data class SessionControl(
    val id: String,
    val labelKey: String,
    val event: MissionControlEvent,
    val icon: IconKind? = null,
    val kind: ButtonKind = ButtonKind.SECONDARY,
)

/** "Attach" and "Odpowiedz" (AM-511) when they apply now. */
internal fun SessionDetails.openControls(): List<SessionControl> =
    listOfNotNull(
        SessionControl("attach", "action.attach", MissionControlEvent.Attach, IconKind.ATTACH).takeIf { canAttach },
        SessionControl("reply", "action.reply", MissionControlEvent.Reply, IconKind.REPLY).takeIf { canReply },
    )

/** Everything the row menu offers: [openControls], then [otherControls]. */
internal fun SessionDetails.allControls(): List<SessionControl> = openControls() + otherControls()

/**
 * Stop, Respawn, Resume, the place assignment and Remove when they apply now, then "Podgląd" (not for a disk
 * session: it has no output, and the panel has its own "Pokaż historię").
 */
internal fun SessionDetails.otherControls(): List<SessionControl> =
    SessionAction.entries.filter { it in actions }.map {
        SessionControl(it.name.lowercase(), "action.${it.name.lowercase()}", MissionControlEvent.Run(it), it.icon())
    } +
        listOfNotNull(
            SessionControl("preview", "action.preview", MissionControlEvent.TogglePreview).takeUnless { fromDisk },
        )

private fun SessionAction.icon(): IconKind =
    when (this) {
        SessionAction.STOP -> IconKind.STOP
        SessionAction.RESPAWN -> IconKind.RESPAWN
        SessionAction.RESUME -> IconKind.RESUME
        SessionAction.REMOVE -> IconKind.REMOVE
        SessionAction.ASSIGN_PLACE -> IconKind.FOLDER
    }

/**
 * The buttons of the details panel (C): the main one first (Odpowiedz, PRIMARY), then the ordinary ones, then the
 * dangerous ones (Stop, Remove; DANGER) apart.
 */
internal fun SessionDetails.detailsControls(): List<SessionControl> {
    val all = allControls()
    val main = all.filter { it.id == "reply" }.map { it.copy(kind = ButtonKind.PRIMARY) }
    val danger = all.filter { it.id in DANGEROUS }.map { it.copy(kind = ButtonKind.DANGER) }
    return main + all.filter { it.id != "reply" && it.id !in DANGEROUS } + danger
}

private val DANGEROUS = setOf("stop", "remove")
