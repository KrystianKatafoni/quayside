package dev.quayside.sessions.impl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Wait before the next listing (AM-403 plan, table "delayAfter"). */
class PollScheduleTest {
    private val schedule = PollSchedule(interval = 2.seconds, maxRetryInterval = 15.seconds)

    @ParameterizedTest(name = "{0} failures -> 2 s")
    @MethodSource("graceFailures")
    fun `waits the poll interval after up to two failures`(failures: Int) {
        schedule.delayAfter(failures) shouldBe 2.seconds
    }

    @ParameterizedTest(name = "{0} failures -> {1} s")
    @MethodSource("doubling")
    fun `doubles the wait after repeated failures`(
        failures: Int,
        expectedSeconds: Long,
    ) {
        schedule.delayAfter(failures) shouldBe expectedSeconds.seconds
    }

    @ParameterizedTest(name = "{0} failures -> 15 s")
    @MethodSource("manyFailures")
    fun `never waits longer than the maximum`(failures: Int) {
        schedule.delayAfter(failures) shouldBe 15.seconds
    }

    @ParameterizedTest(name = "{0} failures with interval = max -> 2 s")
    @MethodSource("manyFailures")
    fun `equal interval and maximum never back off`(failures: Int) {
        PollSchedule(interval = 2.seconds, maxRetryInterval = 2.seconds).delayAfter(failures) shouldBe 2.seconds
    }

    // Durations are passed as milliseconds: JUnit cannot hand a boxed Duration to a value-class parameter.
    @ParameterizedTest(name = "interval {0} ms, max {1} ms")
    @MethodSource("invalid")
    fun `rejects invalid intervals`(
        intervalMillis: Long,
        maxMillis: Long,
    ) {
        shouldThrow<IllegalArgumentException> { PollSchedule(intervalMillis.milliseconds, maxMillis.milliseconds) }
    }

    companion object {
        @JvmStatic
        fun graceFailures(): List<Int> = listOf(0, 1, 2)

        @JvmStatic
        fun doubling(): List<Arguments> =
            listOf(
                arguments(3, 4L),
                arguments(4, 8L),
            )

        @JvmStatic
        fun manyFailures(): List<Int> = listOf(5, 10, 100, Int.MAX_VALUE)

        @JvmStatic
        fun invalid(): List<Arguments> =
            listOf(
                arguments(0L, 15_000L),
                arguments(-1_000L, 15_000L),
                arguments(2_000L, 1_000L),
                arguments(0L, 0L),
            )
    }
}
