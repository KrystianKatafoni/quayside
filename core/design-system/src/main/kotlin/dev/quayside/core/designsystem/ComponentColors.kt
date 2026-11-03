package dev.quayside.core.designsystem

import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba

// Colour pairs of the components. Components read their colours only from here, so the contrast test covers every
// pair on screen (NFR-6, at least 4.5:1 for text). Pairs are (background, foreground); borders have their own
// functions.
private val NO_BACKGROUND = Rgba(0x00000000L)

/**
 * (background, label) of a button. PRIMARY is accentInk on accent, SECONDARY is text on raised. A disabled button
 * uses the dim label: WCAG exempts inactive controls from the contrast minimum.
 */
internal fun buttonColors(
    kind: ButtonKind,
    enabled: Boolean,
    tokens: ColorTokens,
): Pair<Rgba, Rgba> {
    val (background, label) =
        when (kind) {
            ButtonKind.PRIMARY -> tokens.content.accent to tokens.content.accentInk
            ButtonKind.SECONDARY -> tokens.surfaces.raised to tokens.content.text
        }
    return background to if (enabled) label else tokens.content.dim
}

/** (background, text) of a list row; an unselected row is transparent over its container. */
internal fun rowColors(
    selected: Boolean,
    tokens: ColorTokens,
): Pair<Rgba, Rgba> = (if (selected) tokens.surfaces.sel else NO_BACKGROUND) to tokens.content.text

/** (background, text) of the mode bar. */
internal fun modeBarColors(tokens: ColorTokens): Pair<Rgba, Rgba> = tokens.surfaces.panel to tokens.content.muted

/** Colour of the focus frame: text, which the AM-801 audit keeps at 3:1 or more on bg, panel, raised and sel. */
internal fun focusColor(tokens: ColorTokens): Rgba = tokens.content.text

/** (background, label) of the mode badge: accentInk on accent, like a primary button. */
internal fun badgeColors(tokens: ColorTokens): Pair<Rgba, Rgba> = tokens.content.accent to tokens.content.accentInk

/** (fill, border) of a panel; [raised] = raised instead of panel fill. */
internal fun panelColors(
    raised: Boolean,
    tokens: ColorTokens,
): Pair<Rgba, Rgba> = (if (raised) tokens.surfaces.raised else tokens.surfaces.panel) to tokens.surfaces.border

/** Border of a SECONDARY button. */
internal fun buttonBorder(tokens: ColorTokens): Rgba = tokens.surfaces.border2

/** Line on top of the mode bar. */
internal fun modeBarBorder(tokens: ColorTokens): Rgba = tokens.surfaces.border
