package dev.quayside.core.designtokens

import java.util.Locale

/**
 * NFR-6 audit of one theme (docs/12 §1): text pairs need 4.5 to 1, status colours 3 to 1 on `bg` and `panel`,
 * and the hue of `warn`/`ok`/`err` must differ by at least 30 degrees from the colours they could be mistaken for.
 * A translucent `panel` is composited on `bg`. Colours without a hue (greys) never conflict.
 */
public object ContrastAudit {
    private const val MIN_HUE_DISTANCE: Double = 30.0

    /** Every violated rule as `label value < limit`; the label is the part before the first space. */
    public fun problems(colors: ColorTokens): List<String> {
        val panel = colors.surfaces.panel.over(colors.surfaces.bg)
        return textProblems(colors, panel) + statusProblems(colors, panel) + hueProblems(colors) + ansiProblems(colors)
    }

    private fun textProblems(
        colors: ColorTokens,
        panel: Rgba,
    ): List<String> {
        val s = colors.surfaces
        val c = colors.content
        val pairs =
            listOf(
                Triple("text/bg", c.text, s.bg),
                Triple("text/panel", c.text, panel),
                Triple("muted/bg", c.muted, s.bg),
                Triple("muted/panel", c.muted, panel),
                Triple("accentInk/accent", c.accentInk, c.accent),
                Triple("warnInk/warn", colors.status.warnInk, colors.status.warn),
                Triple("text/raised", c.text, s.raised),
                Triple("text/sel", c.text, s.sel),
                Triple("termText/termBg", colors.terminal.termText, colors.terminal.termBg),
            )
        return pairs.mapNotNull { (label, fg, bg) -> contrastProblem(label, fg, bg, Wcag.TEXT_MIN) }
    }

    /**
     * Every ANSI colour needs 4.5 to 1 on `termBg`, except the background colour of the mode: index 0 when `termBg`
     * is closer to black (dark mode), index 15 otherwise (AM-811, ADR 0027). The mode is detected by the luminance of
     * `termBg`, which agrees with `theme.mode` for the 29 catalog themes; it may diverge for user themes (AM-814).
     */
    private fun ansiProblems(colors: ColorTokens): List<String> {
        val termBg = colors.terminal.termBg
        val dark = Wcag.contrast(termBg, Rgba.BLACK) <= Wcag.contrast(termBg, Rgba.WHITE)
        val modeBackground = if (dark) 0 else AnsiColors.SIZE - 1
        return (0 until AnsiColors.SIZE)
            .filter { it != modeBackground }
            .mapNotNull { contrastProblem("ansi$it/termBg", colors.terminal.ansi[it], termBg, Wcag.TEXT_MIN) }
    }

    private fun statusProblems(
        colors: ColorTokens,
        panel: Rgba,
    ): List<String> {
        val st = colors.status
        val statuses = listOf("warn" to st.warn, "ok" to st.ok, "err" to st.err, "work" to st.work)
        return statuses.flatMap { (name, color) ->
            listOfNotNull(
                contrastProblem("$name/bg", color, colors.surfaces.bg, Wcag.NON_TEXT_MIN),
                contrastProblem("$name/panel", color, panel, Wcag.NON_TEXT_MIN),
            )
        }
    }

    private fun hueProblems(colors: ColorTokens): List<String> {
        val st = colors.status
        val accent = colors.content.accent
        val pairs =
            listOf(
                Triple("warn~accent", st.warn, accent),
                Triple("warn~ok", st.warn, st.ok),
                Triple("warn~err", st.warn, st.err),
                Triple("ok~accent", st.ok, accent),
                Triple("err~accent", st.err, accent),
            )
        return pairs.mapNotNull { (label, a, b) ->
            hueDistance(a, b)?.takeIf { it < MIN_HUE_DISTANCE }?.let { "$label ${format(it)} < $MIN_HUE_DISTANCE" }
        }
    }

    private fun contrastProblem(
        label: String,
        foreground: Rgba,
        background: Rgba,
        minimum: Double,
    ): String? =
        Wcag.contrast(foreground, background).takeIf { it < minimum }?.let { "$label ${format(it)} < $minimum" }

    private fun format(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
}
