package dev.quayside.appearance.impl

import dev.quayside.appearance.api.ThemeMode
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.ContentColors
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.StatusColors
import dev.quayside.core.designtokens.SurfaceColors
import dev.quayside.core.designtokens.TerminalColors
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.mix

/**
 * One row of the docs/08 table: [colors] are eight `#RRGGBB` values separated by one space, in column
 * order (bg, panel, text, accent, warn, ok, err, work).
 */
internal data class SchemeRow(
    val key: String,
    val name: String,
    val mode: ThemeMode,
    val colors: String,
    val radius: Int,
) {
    /** The eight listed colours by column. */
    val palette: Map<Column, Rgba> =
        colors.split(' ').mapNotNull(Rgba::parse).let { parsed ->
            require(parsed.size == Column.entries.size && colors.split(' ').size == parsed.size) {
                "row $key needs ${Column.entries.size} valid #RRGGBB colours"
            }
            Column.entries.zip(parsed).toMap()
        }

    /** Columns of the docs/08 table that hold a colour. */
    internal enum class Column { BG, PANEL, TEXT, ACCENT, WARN, OK, ERR, WORK }
}

private const val MUTED_PERCENT = 30
private const val BORDER_PERCENT = 12
internal const val BORDER2_PERCENT = 15
internal const val DIM_PERCENT = 30
internal const val RAISED_LIGHT = 4
internal const val RAISED_DARK = 6
internal const val SEL_LIGHT = 12
internal const val SEL_DARK = 18
internal const val WARN_BG_PERCENT = 15
internal val LIGHT_TERM_BG = Rgba(0xFF1E242CL)
internal val LIGHT_TERM_TEXT = Rgba(0xFFE2E8F0L)

private fun SchemeRow.color(column: SchemeRow.Column): Rgba = palette.getValue(column)

/** Fills the 11 tokens docs/08 does not list from the eight it does (AM-801 plan). */
internal fun deriveTokens(row: SchemeRow): ColorTokens {
    val muted = row.color(SchemeRow.Column.TEXT).mix(row.color(SchemeRow.Column.PANEL), MUTED_PERCENT)
    return ColorTokens(
        surfaces = surfaces(row),
        content = content(row, muted),
        status = status(row),
        terminal = terminal(row),
    )
}

private fun surfaces(row: SchemeRow): SurfaceColors {
    val panel = row.color(SchemeRow.Column.PANEL)
    val text = row.color(SchemeRow.Column.TEXT)
    val dark = row.mode.isDark
    val border = panel.mix(text, BORDER_PERCENT)
    return SurfaceColors(
        bg = row.color(SchemeRow.Column.BG),
        panel = panel,
        raised = panel.mix(text, if (dark) RAISED_DARK else RAISED_LIGHT),
        sel = panel.mix(row.color(SchemeRow.Column.ACCENT), if (dark) SEL_DARK else SEL_LIGHT),
        border = border,
        border2 = border.mix(text, BORDER2_PERCENT),
    )
}

private fun content(
    row: SchemeRow,
    muted: Rgba,
): ContentColors {
    val accent = row.color(SchemeRow.Column.ACCENT)
    return ContentColors(
        text = row.color(SchemeRow.Column.TEXT),
        muted = muted,
        dim = muted.mix(row.color(SchemeRow.Column.BG), DIM_PERCENT),
        accent = accent,
        accentInk = Wcag.inkFor(accent),
    )
}

private fun status(row: SchemeRow): StatusColors {
    val warn = row.color(SchemeRow.Column.WARN)
    return StatusColors(
        warn = warn,
        warnInk = Wcag.inkFor(warn),
        warnBg = row.color(SchemeRow.Column.PANEL).mix(warn, WARN_BG_PERCENT),
        ok = row.color(SchemeRow.Column.OK),
        err = row.color(SchemeRow.Column.ERR),
        work = row.color(SchemeRow.Column.WORK),
    )
}

private fun terminal(row: SchemeRow): TerminalColors =
    if (row.mode.isDark) {
        TerminalColors(row.color(SchemeRow.Column.BG), row.color(SchemeRow.Column.TEXT))
    } else {
        TerminalColors(LIGHT_TERM_BG, LIGHT_TERM_TEXT)
    }
