package dev.quayside.core.designsystem

import androidx.compose.runtime.Composable
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.ContentColors
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.SampleTokens.rgb
import dev.quayside.core.designtokens.ShapeTokens
import dev.quayside.core.designtokens.StatusColors
import dev.quayside.core.designtokens.SurfaceColors
import dev.quayside.core.designtokens.TerminalColors
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.TypographySpec
import dev.quayside.core.designtokens.mix

/** IBM Plex, the default typography of docs/08. */
internal val PLEX_SPEC = TypographySpec(TypographyId("plex"), "IBM Plex", "IBM Plex Sans", "IBM Plex Mono")

/**
 * The light `paper` scheme of docs/08: the eight table colours and the rest derived by the docs/08 rules, built with
 * [mix] only (no dependency on the catalog module).
 */
internal fun paperColors(): ColorTokens {
    val bg = rgb(0xF4F6F8)
    val panel = rgb(0xFFFFFF)
    val text = rgb(0x111827)
    val accent = rgb(0x4338CA)
    val warn = rgb(0xA16207)
    val muted = text.mix(panel, 30)
    return ColorTokens(
        surfaces = paperSurfaces(bg, panel, text, accent),
        content =
            ContentColors(text = text, muted = muted, dim = muted.mix(bg, 30), accent = accent, accentInk = Rgba.WHITE),
        status =
            StatusColors(
                warn = warn,
                warnInk = Rgba.WHITE,
                warnBg = panel.mix(warn, 15),
                ok = rgb(0x15803D),
                err = rgb(0xB91C1C),
                work = accent,
            ),
        terminal = TerminalColors(termBg = rgb(0x1E242C), termText = rgb(0xE2E8F0)),
    )
}

private fun paperSurfaces(
    bg: Rgba,
    panel: Rgba,
    text: Rgba,
    accent: Rgba,
): SurfaceColors {
    val border = panel.mix(text, 12)
    return SurfaceColors(
        bg = bg,
        panel = panel,
        raised = panel.mix(text, 4),
        sel = panel.mix(accent, 12),
        border = border,
        border2 = border.mix(text, 15),
    )
}

/** Shape of the nine base themes, radius 0 here so that tests can tell it from the default 8. */
internal fun squareShape(): ShapeTokens = SampleTokens.shape(radius = 0)

/** Wraps [content] in the default test theme: `plex` colours, 8 dp corners and IBM Plex fonts. */
@Composable
internal fun PlexTheme(content: @Composable () -> Unit) {
    AmTheme(SampleTokens.colors(), SampleTokens.shape(), PLEX_SPEC, content)
}
