package dev.quayside.app

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.appearance.api.UiScale
import dev.quayside.appearance.impl.appearanceModule
import dev.quayside.core.designsystem.AmDensity
import dev.quayside.core.designtokens.TypographyId
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * The look the windows are drawn with (FR-A1, FR-A4, plan AM-803): picked from the catalog by the ids of the
 * settings, never by position, with the list density and the UI scale of the settings.
 */
class LookOfTest {
    private val catalog = appearanceModule().catalog

    private fun ThemeCatalog.themeOf(key: String) = checkNotNull(theme(SchemeId(key))) { "$key theme missing" }

    private fun ThemeCatalog.typographyOf(key: String) =
        checkNotNull(typography(TypographyId(key))) { "$key typography missing" }

    @ParameterizedTest(name = "theme {0} with typography {1}")
    @MethodSource("choices")
    fun `look takes colours and shape of the scheme and the chosen typography`(
        scheme: String,
        typography: String,
    ) {
        val look = catalog.lookOf(AppearanceSettings(SchemeId(scheme), TypographyId(typography)))

        look.colors shouldBe catalog.themeOf(scheme).colors
        look.shape shouldBe catalog.themeOf(scheme).shape
        look.typography shouldBe catalog.typographyOf(typography)
    }

    @Test
    fun `default settings give the plex look`() {
        val look = catalog.lookOf(AppearanceSettings())

        look.colors shouldBe catalog.themeOf("plex").colors
        look.typography shouldBe catalog.typographyOf("plex")
        look.density shouldBe AmDensity.COMFORTABLE
        look.scale shouldBe UiScale.M
    }

    @Test
    fun `look is picked by id not by position in the catalog`() {
        // Given the same catalog in reverse order, so that plex is neither the first theme nor the first typography
        val reversed = catalog.copy(themes = catalog.themes.reversed(), typographies = catalog.typographies.reversed())

        val look = reversed.lookOf(AppearanceSettings())

        look.colors shouldBe catalog.themeOf("plex").colors
        look.typography shouldBe catalog.typographyOf("plex")
    }

    @ParameterizedTest(name = "{0} gives {1}")
    @MethodSource("densities")
    fun `look maps the list density`(
        stored: ListDensity,
        expected: AmDensity,
    ) {
        catalog.lookOf(AppearanceSettings(density = stored)).density shouldBe expected
    }

    @ParameterizedTest(name = "scale {0}")
    @MethodSource("scales")
    fun `look carries the ui scale`(scale: UiScale) {
        catalog.lookOf(AppearanceSettings(uiScale = scale)).scale shouldBe scale
    }

    @ParameterizedTest(name = "{0} gives density {1}")
    @MethodSource("scaledDensities")
    fun `scaling multiplies the density of the window and keeps the font scale`(
        scale: UiScale,
        expectedDensity: Float,
    ) {
        val scaled = Density(density = 2f, fontScale = 1.3f).scaledBy(scale)

        scaled.density shouldBe expectedDensity.plusOrMinus(0.0001f)
        scaled.fontScale shouldBe 1.3f
    }

    @Test
    fun `a scaled window is bigger for L and smaller for S`() {
        val base = Density(density = 2f)

        (12.dp.value * base.scaledBy(UiScale.L).density > 12.dp.value * base.density) shouldBe true
        (12.dp.value * base.scaledBy(UiScale.S).density < 12.dp.value * base.density) shouldBe true
    }

    companion object {
        @JvmStatic
        fun choices(): Stream<Arguments> =
            Stream.of(
                Arguments.of("plex", "plex"),
                Arguments.of("plex", "geist"),
                Arguments.of("paper", "jetbrains"),
                Arguments.of("plex", "system"),
            )

        @JvmStatic
        fun densities(): Stream<Arguments> =
            Stream.of(
                Arguments.of(ListDensity.COMPACT, AmDensity.COMPACT),
                Arguments.of(ListDensity.COMFORTABLE, AmDensity.COMFORTABLE),
            )

        @JvmStatic
        fun scales(): Stream<Arguments> = UiScale.entries.stream().map { Arguments.of(it) }

        @JvmStatic
        fun scaledDensities(): Stream<Arguments> =
            Stream.of(
                Arguments.of(UiScale.S, 1.8f),
                Arguments.of(UiScale.M, 2.0f),
                Arguments.of(UiScale.L, 2.3f),
            )
    }
}
