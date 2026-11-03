package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionState
import kotlin.time.Duration.Companion.minutes

/**
 * The pure core of the notification policy (docs/11 §4): which deliveries [event] causes under
 * [settings]. No clock, no IO. Per-project exceptions come with AM-705 part B.
 *
 * - A session that waits maps to [NotificationKind.NEEDS_INPUT] or [NotificationKind.PERMISSION] by its state.
 * - [SessionEvent.RunningLong] counts only from [NotificationSettings.runningLongMinutes] on.
 * - [NotificationSettings.popups] and [NotificationSettings.voice] silence their channel for every kind.
 * - While [quietNow] (quiet hours or Focus), [NotificationChannel.VOICE] and [NotificationChannel.SOUND] drop.
 * - [NotificationChannel.VOICE] also needs [voiceAllowed] in [context] (window active, microphone in use).
 * - The order of the result is the order of [NotificationChannel].
 */
internal fun decide(
    event: SessionEvent,
    settings: NotificationSettings,
    context: DeliveryContext,
): List<Delivery> {
    val kind = kindOf(event, settings) ?: return emptyList()
    return deliveriesOf(kind, event.session, settings, context)
}

/** The deliveries of a terminal bell (FR-T7): the channels chosen for [NotificationKind.TERMINAL_BELL]. */
internal fun decideTerminalBell(
    settings: NotificationSettings,
    context: DeliveryContext,
): List<Delivery> = deliveriesOf(NotificationKind.TERMINAL_BELL, null, settings, context)

private val SILENCED_IN_QUIET = setOf(NotificationChannel.VOICE, NotificationChannel.SOUND)

private fun deliveriesOf(
    kind: NotificationKind,
    session: Session?,
    settings: NotificationSettings,
    context: DeliveryContext,
): List<Delivery> =
    NotificationChannel.entries
        .filter { it in settings.channelsFor(kind) && globallyOn(it, settings) }
        .filter { it !in SILENCED_IN_QUIET || !quietNow(settings.quietHours, context) }
        .filter { it != NotificationChannel.VOICE || voiceAllowed(settings.speech, context) }
        .map { Delivery(it, kind, session) }

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
