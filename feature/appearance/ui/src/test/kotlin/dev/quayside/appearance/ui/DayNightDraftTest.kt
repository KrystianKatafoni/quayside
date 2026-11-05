package dev.quayside.appearance.ui

import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.DayNight
import dev.quayside.appearance.api.SchemeId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** The pure functions behind the day/night part of the dialog (FR-A5, plan AM-804, table "Testy"). */
class DayNightDraftTest {
    private val scheme = SchemeId("paper")
    private val day = SchemeId("plex")
    private val night = SchemeId("sunset")
    private val chosen = SchemeId("graphite")

    private fun draft(
        enabled: Boolean,
        slot: ThemeSlot,
    ) = AppearanceDraft(
        AppearanceSettings(scheme = scheme, dayNight = DayNight(enabled, day, night)),
        terminalFont = "Menlo",
        terminalSize = 17,
        slot = slot,
    )

    @ParameterizedTest(name = "day night enabled={0}, slot {1} previews {2}")
    @MethodSource("previews")
    fun `preview shows the scheme, or the theme of the edited slot`(
        enabled: Boolean,
        slot: ThemeSlot,
        expected: String,
    ) {
        previewScheme(draft(enabled, slot)) shouldBe SchemeId(expected)
    }

    @ParameterizedTest(name = "day night enabled={0}, slot {1} sets {2}")
    @MethodSource("selections")
    fun `selecting a theme sets the scheme, or the theme of the edited slot`(
        enabled: Boolean,
        slot: ThemeSlot,
        expectedScheme: String,
        expectedDay: String,
        expectedNight: String,
    ) {
        val before = draft(enabled, slot)

        val after = selectInSlot(before, chosen)

        after.settings.scheme shouldBe SchemeId(expectedScheme)
        after.settings.dayNight shouldBe DayNight(enabled, SchemeId(expectedDay), SchemeId(expectedNight))
        after.copy(settings = before.settings) shouldBe before
    }

    @ParameterizedTest(name = "slot {0}")
    @MethodSource("slots")
    fun `the chosen theme becomes the preview of the same slot`(slot: ThemeSlot) {
        previewScheme(selectInSlot(draft(enabled = true, slot = slot), chosen)) shouldBe chosen
    }

    companion object {
        @JvmStatic
        fun previews(): Stream<Arguments> =
            Stream.of(
                Arguments.of(false, ThemeSlot.DAY, "paper"),
                Arguments.of(false, ThemeSlot.NIGHT, "paper"),
                Arguments.of(true, ThemeSlot.DAY, "plex"),
                Arguments.of(true, ThemeSlot.NIGHT, "sunset"),
            )

        @JvmStatic
        fun selections(): Stream<Arguments> =
            Stream.of(
                Arguments.of(false, ThemeSlot.DAY, "graphite", "plex", "sunset"),
                Arguments.of(false, ThemeSlot.NIGHT, "graphite", "plex", "sunset"),
                Arguments.of(true, ThemeSlot.DAY, "paper", "graphite", "sunset"),
                Arguments.of(true, ThemeSlot.NIGHT, "paper", "plex", "graphite"),
            )

        @JvmStatic
        fun slots(): Stream<Arguments> = ThemeSlot.entries.stream().map { Arguments.of(it) }
    }
}
