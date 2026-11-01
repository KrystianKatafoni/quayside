package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.sessions.api.Session

/** One notification to deliver on one [channel] (FR-N1); [session] is null when it is not about a session. */
internal data class Delivery(
    val channel: NotificationChannel,
    val kind: NotificationKind,
    val session: Session?,
)
