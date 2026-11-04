package dev.quayside.app

import dev.quayside.appearance.impl.appearanceModule
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.Wcag
import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import org.junit.jupiter.api.Test

/**
 * NFR-6 for the pairs the screens draw (AM-806), on the real `plex` of the catalog: which token stands on which
 * surface is fixed by the plan (docs/plans/AM-806.md, "Mapowanie"). The whole catalog is audited by AM-803.
 */
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
}
