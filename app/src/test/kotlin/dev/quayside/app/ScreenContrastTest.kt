package dev.quayside.app

import dev.quayside.appearance.api.ThemeSpec
import dev.quayside.appearance.impl.appearanceModule
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.mix
import dev.quayside.core.designtokens.over
import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * NFR-6 for the pairs the screens draw (AM-806), on the real `plex` of the catalog: which token stands on which
 * surface is fixed by the plan (docs/plans/AM-806.md, "Mapowanie"). The whole catalog is audited by AM-803, except
 * terminal `termText`/`termBg` (AM-807), which are audited for the whole catalog here.
 */
private const val TINT_PERCENT = 15

class ScreenContrastTest {
    private val colors = appearanceModule().catalog.defaultLook().colors
    private val surfaces = colors.surfaces
    private val status = colors.status

    private fun Rgba.shouldMeet(
        minimum: Double,
        onSurfaces: Map<String, Rgba>,
        label: String,
    ) {
        onSurfaces.forEach { (name, surface) ->
            withClue("$label on $name") { Wcag.contrast(this, surface) shouldBeGreaterThanOrEqual minimum }
        }
    }

    @Test
    fun `text meets 4_5 on bg panel raised sel and warnBg`() {
        val on =
            mapOf(
                "bg" to surfaces.bg,
                "panel" to surfaces.panel,
                "raised" to surfaces.raised,
                "sel" to surfaces.sel,
                "warnBg" to status.warnBg,
            )

        colors.content.text.shouldMeet(Wcag.TEXT_MIN, on, "text")
    }

    @Test
    fun `muted meets 4_5 on bg and panel`() {
        colors.content.muted.shouldMeet(
            Wcag.TEXT_MIN,
            mapOf("bg" to surfaces.bg, "panel" to surfaces.panel),
            "muted",
        )
    }

    @Test
    fun `muted meets 4_5 on sel and warnBg`() {
        colors.content.muted.shouldMeet(
            Wcag.TEXT_MIN,
            mapOf("sel" to surfaces.sel, "warnBg" to status.warnBg),
            "muted",
        )
    }

    @Test
    fun `status tones as text meet 4_5 on sel`() {
        val on = mapOf("sel" to surfaces.sel)

        status.warn.shouldMeet(Wcag.TEXT_MIN, on, "warn")
        status.work.shouldMeet(Wcag.TEXT_MIN, on, "work")
        status.ok.shouldMeet(Wcag.TEXT_MIN, on, "ok")
        status.err.shouldMeet(Wcag.TEXT_MIN, on, "err")
    }

    @Test
    fun `status tones as text meet 4_5 on bg and panel`() {
        val on = mapOf("bg" to surfaces.bg, "panel" to surfaces.panel)

        status.warn.shouldMeet(Wcag.TEXT_MIN, on, "warn")
        status.work.shouldMeet(Wcag.TEXT_MIN, on, "work")
        status.ok.shouldMeet(Wcag.TEXT_MIN, on, "ok")
        status.err.shouldMeet(Wcag.TEXT_MIN, on, "err")
    }

    @Test
    fun `accentInk meets 4_5 on accent`() {
        colors.content.accentInk.shouldMeet(Wcag.TEXT_MIN, mapOf("accent" to colors.content.accent), "accentInk")
    }

    @Test
    fun `status dots and accent marks meet 3 to 1 on bg panel raised and sel`() {
        val on =
            mapOf("bg" to surfaces.bg, "panel" to surfaces.panel, "raised" to surfaces.raised, "sel" to surfaces.sel)

        listOf(
            "warn" to status.warn,
            "work" to status.work,
            "ok" to status.ok,
            "err" to status.err,
            "accent" to colors.content.accent,
        ).forEach { (name, color) -> color.shouldMeet(Wcag.NON_TEXT_MIN, on, name) }
    }

    /** `tinted(c)` of the design system: panel over bg, mixed with 15 percent of [tone]; `warnBg` and raised stay. */
    private fun chipBackground(tone: String): Rgba =
        when (tone) {
            "neutral" -> surfaces.raised
            "warn" -> status.warnBg
            else -> surfaces.panel.over(surfaces.bg).mix(toneColors.getValue(tone), TINT_PERCENT)
        }

    private val toneColors =
        mapOf("accent" to colors.content.accent, "work" to status.work, "ok" to status.ok, "err" to status.err)

    @Test
    fun `button labels meet 4_5 on their fill`() {
        colors.content.accentInk.shouldMeet(Wcag.TEXT_MIN, mapOf("accent" to colors.content.accent), "primary")
        colors.content.text.shouldMeet(Wcag.TEXT_MIN, mapOf("raised" to surfaces.raised), "secondary")
    }

    @Test
    fun `danger label meets 4_5 on panel`() {
        // The label is err when it is readable on panel, else text (plan AM-809, table 1)
        val panel = surfaces.panel.over(surfaces.bg)
        val label = if (Wcag.contrast(status.err, panel) >= Wcag.TEXT_MIN) status.err else colors.content.text

        label.shouldMeet(Wcag.TEXT_MIN, mapOf("panel" to panel), "danger label")
    }

    @Test
    fun `stat card and chip texts meet 4_5`() {
        val panel = surfaces.panel.over(surfaces.bg)
        colors.content.text.shouldMeet(Wcag.TEXT_MIN, mapOf("panel" to panel), "stat value")
        colors.content.muted.shouldMeet(Wcag.TEXT_MIN, mapOf("panel" to panel), "stat label")
        // A chip label keeps its tone when readable on the tone background, else text
        (toneColors + ("warn" to status.warn) + ("neutral" to colors.content.muted)).forEach { (tone, color) ->
            val background = chipBackground(tone)
            val label =
                if (tone != "neutral" &&
                    Wcag.contrast(color, background) >= Wcag.TEXT_MIN
                ) {
                    color
                } else {
                    colors.content.text
                }
            label.shouldMeet(Wcag.TEXT_MIN, mapOf("$tone chip" to background), "chip label $tone")
        }
    }

    @Test
    fun `status marks meet 3 to 1 on panel raised and sel`() {
        val on = mapOf("panel" to surfaces.panel, "raised" to surfaces.raised, "sel" to surfaces.sel)

        listOf("warn" to status.warn, "work" to status.work, "ok" to status.ok, "err" to status.err)
            .plus("muted" to colors.content.muted)
            .forEach { (name, color) -> color.shouldMeet(Wcag.NON_TEXT_MIN, on, name) }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `terminal text meets 4_5 on terminal background in every theme`(theme: ThemeSpec) {
        val terminal = theme.colors.terminal

        terminal.termText.shouldMeet(Wcag.TEXT_MIN, mapOf("termBg" to terminal.termBg), "termText in ${theme.id.value}")
    }

    companion object {
        @JvmStatic
        fun themes(): Stream<Arguments> =
            appearanceModule()
                .catalog.themes
                .stream()
                .map { Arguments.of(it) }
    }
}
