package dev.quayside.app

import dev.quayside.appearance.api.ThemeSpec
import dev.quayside.appearance.impl.builtInCatalog
import dev.quayside.core.designtokens.AnsiColors
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.mix
import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * NFR-6 for what the terminal draws on `termBg`, on every theme of the catalog (AM-811, ADR 0027): the 15 ANSI colours
 * other than the background colour of the mode, the scroll bar thumb and the active pane frame. The default text on
 * `termBg` is in [ScreenContrastTest]. Cursor and selection are the inverted `termText`/`termBg` pair, covered by that
 * text test. A failing pair is a finding about the catalog, not a reason to loosen the test.
 */
class TerminalContrastTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `ansi colours meet 4_5 on terminal background except the background colour`(theme: ThemeSpec) {
        val terminal = theme.colors.terminal
        val modeBackground = if (theme.mode.isDark) 0 else AnsiColors.SIZE - 1

        (0 until AnsiColors.SIZE).filter { it != modeBackground }.forEach { index ->
            withClue("ansi $index in ${theme.id.value}") {
                Wcag.contrast(terminal.ansi[index], terminal.termBg) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `background colour of the mode keeps the xterm value`(theme: ThemeSpec) {
        val ansi = theme.colors.terminal.ansi

        // Black in a dark mode and white in a light one (index 0 and 15 of the xterm base), as in docs/08.
        if (theme.mode.isDark) ansi[0] shouldBe Rgba.BLACK else ansi[AnsiColors.SIZE - 1] shouldBe Rgba.WHITE
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `scroll bar thumb meets 3 to 1 on terminal background and track`(theme: ThemeSpec) {
        val terminal = theme.colors.terminal
        // A copy of the rule of `scrollbarColors` (AM-810, internal to terminal:impl): change it there, change it here.
        // The rule: track termBg + 6 % termText, thumb the
        // smallest mix of 45..100 % that has 3:1 on the track and on termBg.
        val track = terminal.termBg.mix(terminal.termText, TRACK_PERCENT)
        val thumb =
            (THUMB_FROM..PERCENT_MAX)
                .map { terminal.termBg.mix(terminal.termText, it) }
                .firstOrNull { meets(it, track) && meets(it, terminal.termBg) } ?: terminal.termText

        Wcag.contrast(thumb, terminal.termBg) shouldBeGreaterThanOrEqual Wcag.NON_TEXT_MIN
        Wcag.contrast(thumb, track) shouldBeGreaterThanOrEqual Wcag.NON_TEXT_MIN
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `active pane frame accent meets 3 to 1 on terminal background`(theme: ThemeSpec) {
        val terminal = theme.colors.terminal

        Wcag.contrast(theme.colors.content.accent, terminal.termBg) shouldBeGreaterThanOrEqual Wcag.NON_TEXT_MIN
    }

    private fun meets(
        thumb: Rgba,
        behind: Rgba,
    ): Boolean = Wcag.contrast(thumb, behind) >= Wcag.NON_TEXT_MIN

    companion object {
        private const val TRACK_PERCENT = 6
        private const val THUMB_FROM = 45
        private const val PERCENT_MAX = 100

        @JvmStatic
        fun themes(): Stream<Arguments> =
            builtInCatalog()
                .themes
                .stream()
                .map { Arguments.of(Named.of(it.id.value, it)) }
    }
}
