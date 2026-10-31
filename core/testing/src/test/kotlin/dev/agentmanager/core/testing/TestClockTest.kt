package dev.agentmanager.core.testing

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TestClockTest {
    @Test
    fun `starts at T0 and moves only when advanced`() {
        val clock = TestClock()

        clock.now() shouldBe T0
        clock.now() shouldBe T0

        clock.advanceBy(90.seconds)
        clock.now() shouldBe Instant.parse("2026-01-01T09:01:30Z")
    }

    @Test
    fun `can be set to a specific instant`() {
        val clock = TestClock()
        val later = Instant.parse("2026-10-18T12:00:00Z")

        clock.setTo(later)

        clock.now() shouldBe later
    }

    @Test
    fun `refuses to go backwards`() {
        shouldThrow<IllegalArgumentException> { TestClock().advanceBy((-1).minutes) }
    }
}
