package dev.quayside.app

import dev.quayside.appearance.api.ThemeSpec
import dev.quayside.appearance.impl.builtInCatalog
import dev.quayside.core.designsystem.wordmarkAccent
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.mix
import dev.quayside.core.designtokens.over
import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

private const val TINT_PERCENT = 15
private const val LOGO_BEAM_PERCENT = 90

/**
 * NFR-6 for the pairs that the AM-816 screens draw newly (top bar, status bar, counters, selected rows), on every theme
 * of the catalog. A failing pair is a finding about the catalog, not a reason to loosen the test. The pairs of
 * `ScreenContrastTest` stay as they are.
 */
class ScreenContrastBarsTest {
    private class Pairs(
        val colors: ColorTokens,
        val id: String,
    ) {
        val surfaces = colors.surfaces
        val status = colors.status

        /** The panel as drawn: it may be translucent (glass themes) and stands on bg. */
        val panel: Rgba = surfaces.panel.over(surfaces.bg)

        fun Rgba.shouldMeet(
            minimum: Double,
            on: Rgba,
            label: String,
            surface: String,
        ) {
            withClue("$label on $surface in $id") { Wcag.contrast(this, on) shouldBeGreaterThanOrEqual minimum }
        }
    }

    private fun pairs(theme: ThemeSpec) = Pairs(theme.colors, theme.id.value)

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `mode badge accentInk on accent`(theme: ThemeSpec) =
        with(pairs(theme)) {
            colors.content.accentInk.shouldMeet(Wcag.TEXT_MIN, colors.content.accent, "accentInk", "accent")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `search placeholder muted on raised`(theme: ThemeSpec) =
        with(pairs(theme)) {
            colors.content.muted.shouldMeet(Wcag.TEXT_MIN, surfaces.raised, "placeholder", "raised")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `counter labels on tinted tones`(theme: ThemeSpec) =
        with(pairs(theme)) {
            // The counter is drawn like a chip: the label keeps the tone when readable on its tint, else text
            val tones =
                mapOf(
                    "warn" to (status.warn to status.warnBg),
                    "work" to (status.work to panel.mix(status.work, TINT_PERCENT)),
                    "ok" to (status.ok to panel.mix(status.ok, TINT_PERCENT)),
                    "err" to (status.err to panel.mix(status.err, TINT_PERCENT)),
                )
            tones.forEach { (tone, pair) ->
                val (color, background) = pair
                val label = if (Wcag.contrast(color, background) >= Wcag.TEXT_MIN) color else colors.content.text
                label.shouldMeet(Wcag.TEXT_MIN, background, "counter label $tone", "$tone tint")
            }
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `status bar texts on panel`(theme: ThemeSpec) =
        with(pairs(theme)) {
            status.warn.shouldMeet(Wcag.TEXT_MIN, panel, "waiting count", "panel")
            status.work.shouldMeet(Wcag.TEXT_MIN, panel, "working count", "panel")
            colors.content.muted.shouldMeet(Wcag.TEXT_MIN, panel, "supervisor and notification status", "panel")
            colors.content.text.shouldMeet(Wcag.TEXT_MIN, panel, "project name", "panel")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `freshness dot meets 3 to 1 on panel`(theme: ThemeSpec) =
        with(pairs(theme)) {
            status.ok.shouldMeet(Wcag.NON_TEXT_MIN, panel, "fresh dot", "panel")
            status.err.shouldMeet(Wcag.NON_TEXT_MIN, panel, "stale dot", "panel")
            colors.content.muted.shouldMeet(Wcag.NON_TEXT_MIN, panel, "loading dot", "panel")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `voice chip label on raised`(theme: ThemeSpec) =
        with(pairs(theme)) {
            colors.content.text.shouldMeet(Wcag.TEXT_MIN, surfaces.raised, "voice chip label", "raised")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `label role muted on panel`(theme: ThemeSpec) =
        with(pairs(theme)) {
            colors.content.muted.shouldMeet(Wcag.TEXT_MIN, panel, "PROJEKTY, WIDOKI, PODSUMOWANIE", "panel")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `selected row frame accent meets 3 to 1 on panel`(theme: ThemeSpec) =
        with(pairs(theme)) {
            colors.content.accent.shouldMeet(Wcag.NON_TEXT_MIN, panel, "row frame", "panel")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `wordmark quay text on panel`(theme: ThemeSpec) =
        with(pairs(theme)) {
            colors.content.text.shouldMeet(Wcag.TEXT_MIN, panel, "wordmark Quay", "panel")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `wordmark side on panel`(theme: ThemeSpec) =
        with(pairs(theme)) {
            // The drawn colour of "side": the accent where it reads, else mixed toward the text colour
            colors.wordmarkAccent().shouldMeet(Wcag.TEXT_MIN, panel, "wordmark side", "panel")
        }

    @Test
    fun `logo parts meet 3 to 1 on their tile`() {
        // The mark has fixed colours on a fixed tile (ADR 0030): one case, not one per theme
        val tile = checkNotNull(Rgba.parse("#1E1930"))
        val parts =
            mapOf(
                "beam" to checkNotNull(Rgba.parse("#FBBF24")).withAlpha(LOGO_BEAM_PERCENT).over(tile),
                "lantern" to checkNotNull(Rgba.parse("#E8E5F2")),
                "head and base" to checkNotNull(Rgba.parse("#A78BFA")),
            )
        parts.forEach { (name, color) ->
            withClue("$name on the tile") { Wcag.contrast(color, tile) shouldBeGreaterThanOrEqual Wcag.NON_TEXT_MIN }
        }
    }

    companion object {
        @JvmStatic
        fun themes(): Stream<Arguments> =
            builtInCatalog()
                .themes
                .stream()
                .map { Arguments.of(Named.of(it.id.value, it)) }
    }
}
