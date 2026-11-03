package dev.quayside.notifications.impl

import dev.quayside.notifications.api.QuietHours
import java.time.LocalTime

/**
 * True when [time] is inside the half-open interval [QuietHours.from]..[QuietHours.to], across midnight too (when
 * `to` is before `from`). Equal `from` and `to` is never quiet.
 */
internal fun QuietHours.covers(time: LocalTime): Boolean =
    when {
        from == to -> false
        from < to -> time >= from && time < to
        else -> time >= from || time < to
    }

/** True when the hours cover the local time of [context] or a Focus is on and respected (FR-N6). */
internal fun quietNow(
    quiet: QuietHours,
    context: DeliveryContext,
): Boolean = (quiet.enabled && quiet.covers(context.localTime)) || (quiet.respectFocus && context.focusActive)
