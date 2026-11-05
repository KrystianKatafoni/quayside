package dev.quayside.core.designsystem

import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.mix
import dev.quayside.core.designtokens.over

// Colour rules of tones (docs/08): tinted(c) = panel.over(bg).mix(c, 15), the same rule that gives warnBg in plex.
private const val TINT_PERCENT = 15
private const val DANGER_BORDER_PERCENT = 35
private val NO_BACKGROUND = Rgba(0x00000000L)

/** Colours of a pill or tile: [background], text [label] (4.5:1 or text), [mark] (icon, 3:1 or text). */
internal data class ToneColors(
    val background: Rgba,
    val label: Rgba,
    val mark: Rgba,
)

/** (background, border, icon) of an icon button; [border] null = none. */
internal data class IconButtonColors(
    val background: Rgba,
    val border: Rgba?,
    val icon: Rgba,
)

private fun tinted(
    color: Rgba,
    tokens: ColorTokens,
    percent: Int = TINT_PERCENT,
): Rgba =
    tokens.surfaces.panel
        .over(tokens.surfaces.bg)
        .mix(color, percent)

/** Mark colour of [tone]: muted, accent, warn, work, ok or err. */
internal fun toneColor(
    tone: StatusTone,
    tokens: ColorTokens,
): Rgba =
    when (tone) {
        StatusTone.NEUTRAL -> tokens.content.muted
        StatusTone.ACCENT -> tokens.content.accent
        StatusTone.WARN -> tokens.status.warn
        StatusTone.WORK -> tokens.status.work
        StatusTone.OK -> tokens.status.ok
        StatusTone.ERR -> tokens.status.err
    }

/** Background of [tone]: raised for NEUTRAL, warnBg for WARN, otherwise the tone tinted over the panel. */
internal fun toneBackground(
    tone: StatusTone,
    tokens: ColorTokens,
): Rgba =
    when (tone) {
        StatusTone.NEUTRAL -> tokens.surfaces.raised
        StatusTone.WARN -> tokens.status.warnBg
        else -> tinted(toneColor(tone, tokens), tokens)
    }

/** Tone colours of a pill: label and mark keep the tone when they are readable on the background, else use text. */
internal fun chipColors(
    tone: StatusTone,
    tokens: ColorTokens,
): ToneColors {
    val background = toneBackground(tone, tokens)
    val color = toneColor(tone, tokens)
    val text = tokens.content.text
    val readable = tone != StatusTone.NEUTRAL && Wcag.contrast(color, background) >= Wcag.TEXT_MIN
    val visible = Wcag.contrast(color, background) >= Wcag.NON_TEXT_MIN
    return ToneColors(background, if (readable) color else text, if (visible) color else text)
}

/** Colours of an icon button; disabled uses dim, DANGER is err on a transparent background with a tinted border. */
internal fun iconButtonColors(
    options: IconButtonOptions,
    tokens: ColorTokens,
): IconButtonColors {
    val surfaces = tokens.surfaces
    val style = options.style
    val background =
        when (style) {
            IconButtonStyle.OUTLINED -> surfaces.raised
            IconButtonStyle.SELECTED -> surfaces.sel
            else -> NO_BACKGROUND
        }
    val border =
        when (style) {
            IconButtonStyle.GHOST -> null
            IconButtonStyle.DANGER -> tinted(tokens.status.err, tokens, DANGER_BORDER_PERCENT)
            else -> surfaces.border2
        }
    return IconButtonColors(background, border, iconColor(options, tokens))
}

private fun iconColor(
    options: IconButtonOptions,
    tokens: ColorTokens,
): Rgba =
    when {
        !options.enabled -> tokens.content.dim
        options.style == IconButtonStyle.DANGER -> tokens.status.err
        options.tone != StatusTone.NEUTRAL -> toneColor(options.tone, tokens)
        options.style == IconButtonStyle.GHOST -> tokens.content.muted
        else -> tokens.content.text
    }

/** (background, ink) of the counter badge: warn and warnInk. */
internal fun counterBadgeColors(tokens: ColorTokens): Pair<Rgba, Rgba> = tokens.status.warn to tokens.status.warnInk

/** (text, border) of a key cap: muted and border2. */
internal fun keyCapColors(tokens: ColorTokens): Pair<Rgba, Rgba> = tokens.content.muted to tokens.surfaces.border2
