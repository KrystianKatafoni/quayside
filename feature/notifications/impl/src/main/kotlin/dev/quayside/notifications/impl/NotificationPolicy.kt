package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionState
import kotlin.time.Duration.Companion.minutes

/**
 * The pure core of the notification policy (docs/11 §4): which deliveries [event] causes under
 * [settings]. No clock, no IO. Night mode, Focus and per-project exceptions come with AM-705.
 *
 * - A session that waits maps to [NotificationKind.NEEDS_INPUT] or [NotificationKind.PERMISSION] by its state.
 * - [SessionEvent.RunningLong] counts only from [NotificationSettings.runningLongMinutes] on.
 * - [NotificationSettings.popups] and [NotificationSettings.voice] silence their channel for every kind.
 * - The order of the result is the order of [NotificationChannel].
 */
internal fun decide(
    event: SessionEvent,
    settings: NotificationSettings,
): List<Delivery> {
    val kind = kindOf(event, settings) ?: return emptyList()
    return NotificationChannel.entries
        .filter { it in settings.channelsFor(kind) && globallyOn(it, settings) }
        .map { Delivery(it, kind, event.session) }
}

private fun kindOf(
    event: SessionEvent,
    settings: NotificationSettings,
): NotificationKind? =
    when (event) {
        is SessionEvent.BecameBlocked -> {
            when (event.session.state) {
                is SessionState.AwaitingInput -> NotificationKind.NEEDS_INPUT
                is SessionState.AwaitingPermission -> NotificationKind.PERMISSION
                else -> null
            }
        }

        is SessionEvent.TurnFinished -> {
            NotificationKind.TURN_ENDED
        }

        is SessionEvent.Failed -> {
            NotificationKind.FAILED
        }

        is SessionEvent.PullRequestReady -> {
            NotificationKind.PULL_REQUEST
        }

        is SessionEvent.RunningLong -> {
            NotificationKind.RUNNING_LONG.takeIf { event.duration >= settings.runningLongMinutes.minutes }
        }
    }

private fun globallyOn(
    channel: NotificationChannel,
    settings: NotificationSettings,
): Boolean =
    when (channel) {
        NotificationChannel.POPUP -> settings.popups
        NotificationChannel.VOICE -> settings.voice
        NotificationChannel.SOUND, NotificationChannel.DOCK_BADGE -> true
    }
