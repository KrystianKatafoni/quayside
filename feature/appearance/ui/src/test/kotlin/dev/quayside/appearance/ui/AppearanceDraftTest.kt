package dev.quayside.appearance.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.FakeAppearanceApi
import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.appearance.api.UiScale
import dev.quayside.core.designsystem.AmDensity
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.TypographySpec
import dev.quayside.terminal.api.ShellProfile
import dev.quayside.terminal.api.TerminalSettings
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** The pure functions behind the Appearance dialog (plan AM-803, table "Testy"). */
class AppearanceDraftTest {
    private val catalog = FakeAppearanceApi.showcaseCatalog()

    private fun draft(
        scheme: String,
        typography: String,
    ) = AppearanceDraft(
        AppearanceSettings(scheme = SchemeId(scheme), typography = TypographyId(typography)),
        terminalFont = null,
        terminalSize = 13,
    )

    @ParameterizedTest(name = "theme {0} with typography {1} suggests {2}")
    @MethodSource("suggestions")
    fun `suggested typography is the theme default when it differs from the chosen one`(
        scheme: String,
        typography: String,
        expected: String?,
    ) {
        suggestedTypography(draft(scheme, typography), catalog)?.value shouldBe expected
    }

    @ParameterizedTest(name = "{0} {1} gives {2}")
    @MethodSource("steps")
    fun `terminal size steps by one and stops at the limits`(
        size: Int,
        delta: Int,
        expected: Int,
    ) {
        stepSize(size, delta) shouldBe expected
    }

    @Test
    fun `terminal font choices follow the catalog with from typography first`() {
        terminalFontChoices(catalog) shouldBe listOf(null, "IBM Plex Mono", "Geist Mono", "JetBrains Mono")
    }

    @Test
    fun `a terminal font shared by two typographies is listed once at its first place`() {
        val shared =
            catalog.copy(
                typographies =
                    listOf(
                        TypographySpec(TypographyId("plex"), "IBM Plex", "IBM Plex Sans", "IBM Plex Mono"),
                        TypographySpec(TypographyId("geist"), "Geist", "Geist", "Geist Mono"),
                        TypographySpec(TypographyId("plex-2"), "Plex again", "Inter", "IBM Plex Mono"),
                        TypographySpec(TypographyId("mono"), "JetBrains Mono", "JetBrains Mono", "JetBrains Mono"),
                    ),
            )

        terminalFontChoices(shared) shouldBe listOf(null, "IBM Plex Mono", "Geist Mono", "JetBrains Mono")
    }

    @Test
    fun `terminal font choices come from the catalog it is given`() {
        val one: ThemeCatalog =
            ThemeCatalog(
                catalog.themes.filter { it.defaultTypography == null },
                listOf(TypographySpec(TypographyId("plex"), "IBM Plex", "IBM Plex Sans", "Menlo")),
            )

        terminalFontChoices(one) shouldBe listOf(null, "Menlo")
    }

    @ParameterizedTest(name = "scale {1} in a pane of {0} dp keeps {2} of the base density")
    @MethodSource("previewScales")
    fun `preview density fits the window into the pane and applies the scale`(
        paneWidth: Float,
        scale: UiScale,
        expectedFactor: Float,
    ) {
        val base = Density(density = 2f, fontScale = 1.3f)

        val density = previewDensity(base, paneWidth.dp, scale)

        density.density shouldBe (2f * expectedFactor).plusOrMinus(TOLERANCE)
    }

    @ParameterizedTest(name = "scale {0}")
    @MethodSource("scales")
    fun `preview density keeps the font scale`(scale: UiScale) {
        previewDensity(Density(density = 2f, fontScale = 1.3f), 640.dp, scale).fontScale shouldBe 1.3f
    }

    @Test
    fun `draft starts from the saved appearance and the saved terminal font and size`() {
        val settings = AppearanceSettings(SchemeId("paper"), TypographyId("geist"), UiScale.L, ListDensity.COMPACT)
        val terminal = TerminalSettings(shell = ShellProfile.FISH, fontFamily = "Menlo", fontSize = 17)

        draftOf(settings, terminal) shouldBe AppearanceDraft(settings, terminalFont = "Menlo", terminalSize = 17)
    }

    @Test
    fun `a terminal without own font gives the from typography choice`() {
        val draft = draftOf(AppearanceSettings(), TerminalSettings(fontFamily = null, fontSize = 9))

        draft.terminalFont shouldBe null
        draft.terminalSize shouldBe 9
    }

    @Test
    fun `scale and density of the settings reach the draft`() {
        val settings = AppearanceSettings(uiScale = UiScale.S, density = ListDensity.COMPACT)

        val draft = draftOf(settings, TerminalSettings())

        draft.settings.uiScale shouldBe UiScale.S
        draft.settings.density shouldBe ListDensity.COMPACT
    }

    @ParameterizedTest(name = "{0} becomes {1}")
    @MethodSource("densities")
    fun `stored density maps to the design system density`(
        stored: ListDensity,
        expected: AmDensity,
    ) {
        stored.toAmDensity() shouldBe expected
    }

    companion object {
        private const val TOLERANCE = 0.0001f

        @JvmStatic
        fun suggestions(): Stream<Arguments> =
            Stream.of(
                // plex suggests nothing
                Arguments.of("plex", "plex", null),
                Arguments.of("plex", "geist", null),
                // paper suggests geist: nothing when it is chosen already
                Arguments.of("paper", "geist", null),
                Arguments.of("paper", "plex", "geist"),
                Arguments.of("paper", "mono", "geist"),
                // sunset suggests plex
                Arguments.of("sunset", "geist", "plex"),
                Arguments.of("sunset", "plex", null),
                // a theme outside the catalog suggests nothing
                Arguments.of("gone", "plex", null),
            )

        @JvmStatic
        fun steps(): Stream<Arguments> =
            Stream.of(
                Arguments.of(13, 1, 14),
                Arguments.of(13, -1, 12),
                Arguments.of(32, 1, 32),
                Arguments.of(31, 1, 32),
                Arguments.of(8, -1, 8),
                Arguments.of(9, -1, 8),
                Arguments.of(32, -1, 31),
                Arguments.of(8, 1, 9),
            )

        @JvmStatic
        fun previewScales(): Stream<Arguments> =
            Stream.of(
                Arguments.of(640f, UiScale.M, 0.5f),
                Arguments.of(640f, UiScale.L, 0.5f * 1.15f),
                Arguments.of(640f, UiScale.S, 0.5f * 0.9f),
                Arguments.of(1280f, UiScale.M, 1f),
                Arguments.of(320f, UiScale.M, 0.25f),
            )

        @JvmStatic
        fun scales(): Stream<Arguments> = UiScale.entries.stream().map { Arguments.of(it) }

        @JvmStatic
        fun densities(): Stream<Arguments> =
            Stream.of(
                Arguments.of(ListDensity.COMPACT, AmDensity.COMPACT),
                Arguments.of(ListDensity.COMFORTABLE, AmDensity.COMFORTABLE),
            )
    }
}
