package dev.quayside.projects.ui

import dev.quayside.core.designsystem.StatusTone
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.over

/**
 * (fill, ring, label) of a zero counter: fill = the row background (sel or panel over bg, opaque), ring = the status
 * token of [tone], label = muted, or text when muted is below 4.5:1 on the fill.
 */
internal data class ZeroCounterPaint(
    val fill: Rgba,
    val ring: Rgba,
    val label: Rgba,
)

/** The colours of a zero counter of [tone] on a row that is [selected] or not, from [tokens] only. */
internal fun zeroCounterColors(
    tone: StatusTone,
    selected: Boolean,
    tokens: ColorTokens,
): ZeroCounterPaint {
    val fill = if (selected) tokens.surfaces.sel else tokens.surfaces.panel.over(tokens.surfaces.bg)
    val ring =
        when (tone) {
            StatusTone.WARN -> tokens.status.warn
            StatusTone.WORK -> tokens.status.work
            StatusTone.OK -> tokens.status.ok
            StatusTone.ERR -> tokens.status.err
            StatusTone.ACCENT -> tokens.content.accent
            StatusTone.NEUTRAL -> tokens.content.muted
        }
    val muted = tokens.content.muted
    val label = if (Wcag.contrast(muted, fill) >= Wcag.TEXT_MIN) muted else tokens.content.text
    return ZeroCounterPaint(fill, ring, label)
}
