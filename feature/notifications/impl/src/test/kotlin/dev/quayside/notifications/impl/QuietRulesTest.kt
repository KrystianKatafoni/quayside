package dev.quayside.notifications.impl

import dev.quayside.notifications.api.QuietHours
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalTime

/** The quiet-hours rule (FR-N6): a half-open interval that may cross midnight, plus the Focus switch. */
class QuietRulesTest {
    private fun hours(
        from: String,
        to: String,
    ) = QuietHours(enabled = true, from = LocalTime.parse(from), to = LocalTime.parse(to))

    @ParameterizedTest(name = "{0}-{1} at {2} is {3}")
    @MethodSource("coverage")
    fun `covers is a half open interval that may cross midnight`(
        from: String,
        to: String,
        time: String,
        expected: Boolean,
    ) {
        hours(from, to).covers(LocalTime.parse(time)) shouldBe expected
    }

    @Test
    fun `covers does not look at the enabled flag`() {
        QuietHours(enabled = false).covers(LocalTime.of(23, 0)) shouldBe true
    }

    @Test
    fun `quiet now follows hours and focus`() {
        val night = ctx(localTime = LocalTime.of(23, 0))
        val day = ctx(localTime = LocalTime.NOON)
        val focus = ctx(localTime = LocalTime.NOON, focusActive = true)
        val nightFocus = ctx(localTime = LocalTime.of(23, 0), focusActive = true)
        val on = QuietHours(enabled = true)
        val off = QuietHours(enabled = false)

        quietNow(on, night) shouldBe true
        quietNow(on, day) shouldBe false
        quietNow(off, night) shouldBe false
        quietNow(off, focus) shouldBe true
        quietNow(off.copy(respectFocus = false), focus) shouldBe false
        quietNow(on.copy(respectFocus = false), nightFocus) shouldBe true
        quietNow(on.copy(respectFocus = false), focus) shouldBe false
        quietNow(on, focus) shouldBe true
        quietNow(off, day) shouldBe false
    }

    companion object {
        @JvmStatic
        fun coverage(): List<Arguments> =
            listOf(
                arguments("22:00", "07:00", "21:59", false),
                arguments("22:00", "07:00", "22:00", true),
                arguments("22:00", "07:00", "03:00", true),
                arguments("22:00", "07:00", "06:59", true),
                arguments("22:00", "07:00", "07:00", false),
                arguments("22:00", "07:00", "12:00", false),
                arguments("08:00", "17:00", "07:59", false),
                arguments("08:00", "17:00", "08:00", true),
                arguments("08:00", "17:00", "16:59", true),
                arguments("08:00", "17:00", "17:00", false),
                arguments("00:00", "07:00", "00:00", true),
                arguments("00:00", "07:00", "23:59", false),
                arguments("22:00", "22:00", "22:00", false),
                arguments("22:00", "22:00", "03:00", false),
            )
    }
}
