package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.ProjectProfile

/**
 * True when a session of a project with [profile] may deliver [kind] on [channel] (FR-N7). The profile only
 * narrows what the matrix chose: [ProjectProfile.ERRORS_ONLY] keeps every channel of [NotificationKind.FAILED] and
 * nothing else, [ProjectProfile.MUTED] keeps only [NotificationChannel.DOCK_BADGE].
 */
internal fun profileAllows(
    profile: ProjectProfile,
    kind: NotificationKind,
    channel: NotificationChannel,
): Boolean =
    when (profile) {
        ProjectProfile.DEFAULT -> true
        ProjectProfile.ERRORS_ONLY -> kind == NotificationKind.FAILED
        ProjectProfile.MUTED -> channel == NotificationChannel.DOCK_BADGE
    }
