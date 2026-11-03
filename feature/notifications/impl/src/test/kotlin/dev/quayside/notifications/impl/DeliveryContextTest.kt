package dev.quayside.notifications.impl

import dev.quayside.notifications.api.QuietHours
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** The local time of a delivery across daylight saving changes in Europe/Warsaw (FR-N6, plan AM-705 table 2). */
class DeliveryContextTest {
    private fun quiet(
        instant: String,
        zone: ZoneId,
        from: String,
        to: String,
    ): Boolean {
        val time = localTimeAt(Instant.parse(instant), zone)
        val hours = QuietHours(enabled = true, from = LocalTime.parse(from), to = LocalTime.parse(to))
        return quietNow(hours, ctx(localTime = time))
    }

    @ParameterizedTest(name = "{0} {2}-{3} is {4}")
    @MethodSource("changes")
    fun `quiet hours follow the local time across daylight saving changes`(
        instant: String,
        zone: String,
        from: String,
        to: String,
        expected: Boolean,
    ) {
        quiet(instant, ZoneId.of(zone), from, to) shouldBe expected
    }

    @ParameterizedTest(name = "{0} in {1} is {2}")
    @MethodSource("localTimes")
    fun `local time is the clock time of the zone`(
        instant: String,
        zone: String,
        expected: String,
    ) {
        localTimeAt(Instant.parse(instant), ZoneId.of(zone)) shouldBe LocalTime.parse(expected)
    }

    @Test
    fun `the same moment is quiet in one zone and not in another`() {
        val moment = "2026-11-03T21:30:00Z"

        quiet(moment, WARSAW, "22:00", "07:00") shouldBe true
        quiet(moment, ZoneId.of("UTC"), "22:00", "07:00") shouldBe false
    }

    companion object {
        private const val WARSAW_ID = "Europe/Warsaw"

        @JvmStatic
        fun changes(): List<Arguments> =
            listOf(
                arguments("2026-03-29T00:59:59Z", WARSAW_ID, "02:30", "03:30", false),
                arguments("2026-03-29T01:00:00Z", WARSAW_ID, "02:30", "03:30", true),
                arguments("2026-10-25T00:30:00Z", WARSAW_ID, "02:00", "03:00", true),
                arguments("2026-10-25T01:30:00Z", WARSAW_ID, "02:00", "03:00", true),
                arguments("2026-10-25T02:00:00Z", WARSAW_ID, "02:00", "03:00", false),
            )

        @JvmStatic
        fun localTimes(): List<Arguments> =
            listOf(
                arguments("2026-03-29T00:59:59Z", WARSAW_ID, "01:59:59"),
                arguments("2026-03-29T01:00:00Z", WARSAW_ID, "03:00:00"),
                arguments("2026-10-25T00:30:00Z", WARSAW_ID, "02:30:00"),
                arguments("2026-10-25T01:30:00Z", WARSAW_ID, "02:30:00"),
                arguments("2026-10-25T02:00:00Z", WARSAW_ID, "03:00:00"),
                arguments("2026-11-03T21:30:00Z", WARSAW_ID, "22:30:00"),
                arguments("2026-11-03T21:30:00Z", "UTC", "21:30:00"),
            )
    }
}
