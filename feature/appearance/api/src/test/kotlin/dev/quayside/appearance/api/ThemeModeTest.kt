package dev.quayside.appearance.api

import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ThemeModeTest {
    @ParameterizedTest(name = "{0} is dark: {1}, high contrast: {2}")
    @CsvSource(
        "LIGHT, false, false",
        "DARK, true, false",
        "LIGHT_HIGH_CONTRAST, false, true",
        "DARK_HIGH_CONTRAST, true, true",
    )
    fun `mode knows darkness and contrast`(
        mode: ThemeMode,
        dark: Boolean,
        highContrast: Boolean,
    ) {
        mode.isDark shouldBe dark
        mode.isHighContrast shouldBe highContrast
    }
}
