package dev.quayside.app

import dev.quayside.appearance.api.ThemeSpec
import dev.quayside.appearance.impl.appearanceModule
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.mix
import dev.quayside.core.designtokens.over
import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * NFR-6 for the pairs the screens draw (AM-806), on every theme of the catalog (AM-803): which token stands on which
 * surface is fixed by the plan (docs/plans/AM-806.md, "Mapowanie"). Each test runs once per theme; the name of the
 * theme is in the report. A failing pair is a finding about the catalog, not a reason to loosen the test.
 */
private const val TINT_PERCENT = 15

class ScreenContrastTest {
    /** The tokens of one theme under the short names the pairs use. */
    private class Pairs(
        val colors: ColorTokens,
        val id: String,
    ) {
        val surfaces = colors.surfaces
        val status = colors.status

        /** The panel as drawn: it may be translucent (glass themes) and stands on bg. */
        val panel: Rgba = surfaces.panel.over(surfaces.bg)
        val toneColors =
            mapOf("accent" to colors.content.accent, "work" to status.work, "ok" to status.ok, "err" to status.err)

        /** `tinted(c)` of the design system: panel mixed with 15 percent of the tone; `warnBg` and raised stay. */
        fun chipBackground(tone: String): Rgba =
            when (tone) {
                "neutral" -> surfaces.raised
                "warn" -> status.warnBg
                else -> panel.mix(toneColors.getValue(tone), TINT_PERCENT)
            }

        fun Rgba.shouldMeet(
            minimum: Double,
            onSurfaces: Map<String, Rgba>,
            label: String,
        ) {
            onSurfaces.forEach { (name, surface) ->
                withClue("$label on $name in $id") { Wcag.contrast(this, surface) shouldBeGreaterThanOrEqual minimum }
            }
        }
    }

    private fun pairs(theme: ThemeSpec) = Pairs(theme.colors, theme.id.value)

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `text meets 4_5 on bg panel raised sel and warnBg`(theme: ThemeSpec) =
        with(pairs(theme)) {
            val on =
                mapOf(
                    "bg" to surfaces.bg,
                    "panel" to panel,
                    "raised" to surfaces.raised,
                    "sel" to surfaces.sel,
                    "warnBg" to status.warnBg,
                )

            colors.content.text.shouldMeet(Wcag.TEXT_MIN, on, "text")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `muted meets 4_5 on bg and panel`(theme: ThemeSpec) =
        with(pairs(theme)) {
            colors.content.muted.shouldMeet(
                Wcag.TEXT_MIN,
                mapOf("bg" to surfaces.bg, "panel" to panel),
                "muted",
            )
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `muted meets 4_5 on sel and warnBg`(theme: ThemeSpec) =
        with(pairs(theme)) {
            colors.content.muted.shouldMeet(
                Wcag.TEXT_MIN,
                mapOf("sel" to surfaces.sel, "warnBg" to status.warnBg),
                "muted",
            )
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `status tones as text meet 4_5 on sel`(theme: ThemeSpec) =
        with(pairs(theme)) {
            val on = mapOf("sel" to surfaces.sel)

            status.warn.shouldMeet(Wcag.TEXT_MIN, on, "warn")
            status.work.shouldMeet(Wcag.TEXT_MIN, on, "work")
            status.ok.shouldMeet(Wcag.TEXT_MIN, on, "ok")
            status.err.shouldMeet(Wcag.TEXT_MIN, on, "err")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `status tones as text meet 4_5 on bg and panel`(theme: ThemeSpec) =
        with(pairs(theme)) {
            val on = mapOf("bg" to surfaces.bg, "panel" to panel)

            status.warn.shouldMeet(Wcag.TEXT_MIN, on, "warn")
            status.work.shouldMeet(Wcag.TEXT_MIN, on, "work")
            status.ok.shouldMeet(Wcag.TEXT_MIN, on, "ok")
            status.err.shouldMeet(Wcag.TEXT_MIN, on, "err")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `accentInk meets 4_5 on accent`(theme: ThemeSpec) =
        with(pairs(theme)) {
            colors.content.accentInk.shouldMeet(Wcag.TEXT_MIN, mapOf("accent" to colors.content.accent), "accentInk")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `status dots and accent marks meet 3 to 1 on bg panel raised and sel`(theme: ThemeSpec) =
        with(pairs(theme)) {
            val on =
                mapOf(
                    "bg" to surfaces.bg,
                    "panel" to panel,
                    "raised" to surfaces.raised,
                    "sel" to surfaces.sel,
                )

            listOf(
                "warn" to status.warn,
                "work" to status.work,
                "ok" to status.ok,
                "err" to status.err,
                "accent" to colors.content.accent,
            ).forEach { (name, color) -> color.shouldMeet(Wcag.NON_TEXT_MIN, on, name) }
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `button labels meet 4_5 on their fill`(theme: ThemeSpec) =
        with(pairs(theme)) {
            colors.content.accentInk.shouldMeet(Wcag.TEXT_MIN, mapOf("accent" to colors.content.accent), "primary")
            colors.content.text.shouldMeet(Wcag.TEXT_MIN, mapOf("raised" to surfaces.raised), "secondary")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `danger label meets 4_5 on panel`(theme: ThemeSpec) =
        with(pairs(theme)) {
            // The label is err when it is readable on panel, else text (plan AM-809, table 1)
            val label = if (Wcag.contrast(status.err, panel) >= Wcag.TEXT_MIN) status.err else colors.content.text

            label.shouldMeet(Wcag.TEXT_MIN, mapOf("panel" to panel), "danger label")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `stat card and chip texts meet 4_5`(theme: ThemeSpec) =
        with(pairs(theme)) {
            colors.content.text.shouldMeet(Wcag.TEXT_MIN, mapOf("panel" to panel), "stat value")
            colors.content.muted.shouldMeet(Wcag.TEXT_MIN, mapOf("panel" to panel), "stat label")
            // A chip label keeps its tone when readable on the tone background, else text
            (toneColors + ("warn" to status.warn) + ("neutral" to colors.content.muted)).forEach { (tone, color) ->
                val background = chipBackground(tone)
                val keepsTone = tone != "neutral" && Wcag.contrast(color, background) >= Wcag.TEXT_MIN
                val label = if (keepsTone) color else colors.content.text
                label.shouldMeet(Wcag.TEXT_MIN, mapOf("$tone chip" to background), "chip label $tone")
            }
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `status marks meet 3 to 1 on panel raised and sel`(theme: ThemeSpec) =
        with(pairs(theme)) {
            val on = mapOf("panel" to panel, "raised" to surfaces.raised, "sel" to surfaces.sel)

            listOf("warn" to status.warn, "work" to status.work, "ok" to status.ok, "err" to status.err)
                .plus("muted" to colors.content.muted)
                .forEach { (name, color) -> color.shouldMeet(Wcag.NON_TEXT_MIN, on, name) }
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `terminal text meets 4_5 on terminal background in every theme`(theme: ThemeSpec) =
        with(pairs(theme)) {
            val terminal = colors.terminal

            terminal.termText.shouldMeet(Wcag.TEXT_MIN, mapOf("termBg" to terminal.termBg), "termText")
        }

    companion object {
        @JvmStatic
        fun themes(): Stream<Arguments> =
            appearanceModule()
                .catalog.themes
                .stream()
                .map { Arguments.of(Named.of(it.id.value, it)) }
    }
}
