package dev.agentmanager.core.testing

import dev.agentmanager.core.domain.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/** Fixed instant used as "now" in tests unless a test needs a specific date. */
public val T0: Instant = Instant.parse("2026-01-01T09:00:00Z")

/** Manually driven [Clock]: time moves only when the test says so. */
public class TestClock(
    start: Instant = T0,
) : Clock {
    private var current: Instant = start

    override fun now(): Instant = current

    /** Moves time forward; negative durations are rejected because real clocks never go back. */
    public fun advanceBy(duration: Duration) {
        require(!duration.isNegative()) { "TestClock cannot go backwards: $duration" }
        current = current.plus(duration.toJavaDuration())
    }

    public fun setTo(instant: Instant) {
        current = instant
    }
}
