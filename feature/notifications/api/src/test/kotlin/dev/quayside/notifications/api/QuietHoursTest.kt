package dev.quayside.notifications.api

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalTime

/** [QuietHours]: off by default, docs/07 hours, whole minutes only (FR-N6). */
class QuietHoursTest {
    @Test
    fun `defaults are off with docs 07 hours`() {
        val q = QuietHours()

        q.enabled shouldBe false
        q.from shouldBe LocalTime.of(22, 0)
        q.to shouldBe LocalTime.of(7, 0)
        q.respectFocus shouldBe true
        QuietHours.DEFAULT_FROM shouldBe LocalTime.of(22, 0)
        QuietHours.DEFAULT_TO shouldBe LocalTime.of(7, 0)
        NotificationSettings().quietHours shouldBe q
    }

    @ParameterizedTest(name = "from {0}, to {1}")
    @MethodSource("withSeconds")
    fun `rejects seconds`(
        from: LocalTime,
        to: LocalTime,
    ) {
        shouldThrow<IllegalArgumentException> { QuietHours(from = from, to = to) }
    }

    @Test
    fun `accepts whole minutes including midnight and the last minute`() {
        QuietHours(from = LocalTime.MIDNIGHT, to = LocalTime.of(23, 59)).let {
            it.from shouldBe LocalTime.MIDNIGHT
            it.to shouldBe LocalTime.of(23, 59)
        }
        QuietHours(from = LocalTime.of(8, 0), to = LocalTime.of(8, 0)).from shouldBe LocalTime.of(8, 0)
    }

    companion object {
        private val OK = LocalTime.of(7, 0)

        @JvmStatic
        fun withSeconds(): List<Arguments> =
            listOf(
                arguments(LocalTime.of(22, 0, 1), OK),
                arguments(OK, LocalTime.of(7, 0, 30)),
                arguments(LocalTime.of(22, 0).withNano(1), OK),
                arguments(OK, LocalTime.of(7, 0).withNano(1_000_000)),
            )
    }
}
