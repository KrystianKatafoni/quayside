package dev.quayside.notifications.impl

import dev.quayside.notifications.api.SpeechSettings
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * What is true at the moment of a delivery (docs/11 §4): a window of the app is active, a microphone records, the
 * local time of day and whether a macOS Focus is on.
 */
internal data class DeliveryContext(
    val appActive: Boolean,
    val microphoneActive: Boolean,
    val localTime: LocalTime,
    val focusActive: Boolean,
)

/**
 * The local time of day at [instant] in [zone] (the zone is read at every event, so a change applies at once).
 * Around a clock change the wall time decides: in a skipped hour (spring) quiet hours 02:30..03:30 cover 03:00..03:30;
 * in a repeated hour (autumn) quiet hours 02:00..03:00 last two real hours.
 */
internal fun localTimeAt(
    instant: Instant,
    zone: ZoneId,
): LocalTime = LocalTime.ofInstant(instant, zone)

/** True when the voice may speak under [speech] in [context] (FR-N5). */
internal fun voiceAllowed(
    speech: SpeechSettings,
    context: DeliveryContext,
): Boolean =
    !(speech.onlyInBackground && context.appActive) &&
        !(speech.muteWhenMicrophoneActive && context.microphoneActive)
