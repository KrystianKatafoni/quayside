package dev.agentmanager.core.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class ClockTest {
    @Test
    fun `clock is a functional interface usable with a lambda`() {
        val fixed = Instant.parse("2026-10-18T10:00:00Z")
        val clock = Clock { fixed }

        clock.now() shouldBe fixed
    }

    @Test
    fun `system clock delegates to the JVM clock`() {
        // Only a smoke test of the production wiring; logic under test always gets TestClock.
        (SystemClock.now() > Instant.EPOCH) shouldBe true
    }
}
