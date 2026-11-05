package dev.quayside.projects.ui

import dev.quayside.core.designsystem.StatusTone
import dev.quayside.sessions.api.SessionCounts

/** Tone of the folder icon of a project: WARN when a session waits, else WORK when one works, else NEUTRAL. */
internal fun projectTone(counts: SessionCounts?): StatusTone =
    when {
        counts == null -> StatusTone.NEUTRAL
        counts.waiting > 0 -> StatusTone.WARN
        counts.working > 0 -> StatusTone.WORK
        else -> StatusTone.NEUTRAL
    }

/** Label of a rail button: the name, then the non-zero counts, e.g. "Quayside, 2 czekają, 1 pracuje". */
internal fun railLabel(
    name: String,
    counts: SessionCounts?,
): String =
    listOfNotNull(
        name,
        counts?.waiting?.takeIf { it > 0 }?.let { countText("rail.waiting", it) },
        counts?.working?.takeIf { it > 0 }?.let { countText("rail.working", it) },
    ).joinToString(", ")

/** Polish plural: 1 czeka, 2-4 czekają (not 12-14), the rest czeka. */
private fun countText(
    key: String,
    count: Int,
): String {
    val form =
        when {
            count == 1 -> "one"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "few"
            else -> "many"
        }
    return ProjectsStrings.format(UiText("$key.$form", listOf("$count")))
}
