package dev.quayside.notifications.impl

import java.time.LocalTime
import java.time.ZoneId

/** The zone of the quiet-hours tests (AM-705): it has daylight saving time, so the edge cases are real. */
internal val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")

/** A [DeliveryContext] at noon with nothing active unless a test says so; noon is outside the default quiet hours. */
internal fun ctx(
    appActive: Boolean = false,
    microphoneActive: Boolean = false,
    localTime: LocalTime = LocalTime.NOON,
    focusActive: Boolean = false,
): DeliveryContext = DeliveryContext(appActive, microphoneActive, localTime, focusActive)
