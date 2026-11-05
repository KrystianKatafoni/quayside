package dev.quayside.appearance.api

import dev.quayside.core.designtokens.TypographyId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** `appliedFor`: which theme is drawn for the macOS mode (FR-A5, plan AM-804, table "Testy"). */
class AppliedForTest {
    private val scheme = SchemeId("plex")
    private val day = SchemeId("paper")
    private val night = SchemeId("sunset")

    private fun settings(enabled: Boolean) =
        AppearanceSettings(
            scheme = scheme,
            typography = TypographyId("geist"),
            uiScale = UiScale.L,
            density = ListDensity.COMPACT,
            dayNight = DayNight(enabled, day, night),
        )

    @ParameterizedTest(name = "day night enabled={0}, system dark={1} draws {2}")
    @MethodSource("cases")
    fun `applied scheme follows the system mode only when day night is on and the mode is known`(
        enabled: Boolean,
        systemDark: Boolean?,
        expected: String,
    ) {
        settings(enabled).appliedFor(systemDark).scheme shouldBe SchemeId(expected)
    }

    @ParameterizedTest(name = "day night enabled={0}, system dark={1}")
    @MethodSource("cases")
    fun `applied keeps every other field`(
        enabled: Boolean,
        systemDark: Boolean?,
    ) {
        val before = settings(enabled)

        before.appliedFor(systemDark).copy(scheme = scheme) shouldBe before
    }

    @ParameterizedTest(name = "system dark={0}")
    @MethodSource("modes")
    fun `settings without day night are returned as they are`(systemDark: Boolean?) {
        val before = settings(enabled = false)

        before.appliedFor(systemDark) shouldBe before
    }

    @Test
    fun `default day night is off with paper by day and plex by night`() {
        DayNight() shouldBe DayNight(enabled = false, day = SchemeId("paper"), night = SchemeId("plex"))
        DayNight.DEFAULT_DAY shouldBe SchemeId("paper")
        AppearanceSettings().dayNight shouldBe DayNight()
    }

    companion object {
        @JvmStatic
        fun cases(): Stream<Arguments> =
            Stream.of(
                Arguments.of(false, true, "plex"),
                Arguments.of(false, false, "plex"),
                Arguments.of(false, null, "plex"),
                Arguments.of(true, true, "sunset"),
                Arguments.of(true, false, "paper"),
                Arguments.of(true, null, "plex"),
            )

        @JvmStatic
        fun modes(): Stream<Arguments> = Stream.of(Arguments.of(true), Arguments.of(false), Arguments.of(null))
    }
}
