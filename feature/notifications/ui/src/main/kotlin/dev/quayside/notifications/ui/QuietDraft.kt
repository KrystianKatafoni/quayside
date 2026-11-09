package dev.quayside.notifications.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.flatMap
import dev.quayside.notifications.api.QuietHours
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The do-not-disturb part of [NotificationDraft] (FR-N6); [from] and [to] stay text (`HH:MM`) until save. */
public data class QuietDraft(
    val enabled: Boolean,
    val from: String,
    val to: String,
    val respectFocus: Boolean,
)

/** The draft of [quiet]: the times as `HH:MM`. */
internal fun quietDraftOf(quiet: QuietHours): QuietDraft =
    QuietDraft(quiet.enabled, quiet.from.format(CLOCK), quiet.to.format(CLOCK), quiet.respectFocus)

/** `text.trim()` as `H:MM` or `HH:MM` within 00:00..23:59, else null. */
internal fun parseTime(text: String): LocalTime? =
    TIME.matchEntire(text.trim())?.destructured?.let { (hour, minute) ->
        LocalTime.of(hour.toInt(), minute.toInt())
    }

/** Quiet hours over [current]: from, then to; equal times are refused only when enabled. */
internal fun quietOf(
    draft: QuietDraft,
    current: QuietHours,
): Outcome<QuietHours, InvalidField> =
    time(draft.from, NotificationField.QUIET_FROM).flatMap { from ->
        time(draft.to, NotificationField.QUIET_TO).flatMap { to ->
            if (draft.enabled && from == to) {
                Outcome.Err(InvalidField(NotificationField.QUIET_TO))
            } else {
                Outcome.Ok(current.copy(draft.enabled, from, to, draft.respectFocus))
            }
        }
    }

private fun time(
    text: String,
    field: NotificationField,
): Outcome<LocalTime, InvalidField> {
    val value = parseTime(text)
    return if (value == null) Outcome.Err(InvalidField(field)) else Outcome.Ok(value)
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private val TIME = Regex("""([01]?\d|2[0-3]):([0-5]\d)""")
