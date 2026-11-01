package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.sessions.api.Session

/** One notification to deliver on one [channel] (FR-N1); the adapters of the channels consume them. */
internal data class Delivery(
    val channel: NotificationChannel,
    val kind: NotificationKind,
    val session: Session,
)
