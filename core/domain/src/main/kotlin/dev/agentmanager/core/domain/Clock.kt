package dev.agentmanager.core.domain

import java.time.Instant

/** Source of the current time. Injected everywhere instead of calling `Instant.now()` (ENG-C6). */
public fun interface Clock {
    public fun now(): Instant
}

/** Production clock; tests use `TestClock` from `core:testing`. */
public object SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}
