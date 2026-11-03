package dev.quayside.appearance.impl

import dev.quayside.appearance.api.ThemeMode
import dev.quayside.core.designtokens.Backdrop
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.ContentColors
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.ShadowSpec
import dev.quayside.core.designtokens.ShapeTokens
import dev.quayside.core.designtokens.StatusColors
import dev.quayside.core.designtokens.SurfaceColors
import dev.quayside.core.designtokens.TerminalColors
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.mix
import dev.quayside.core.designtokens.over

private const val MUTED_MIN = Wcag.TEXT_MIN
private const val WORK_MIN = Wcag.NON_TEXT_MIN
private const val SHADOW_PARTS = 5
private const val SHADOW_COLOUR_AT = 3
private const val SHAPE_PARTS = 3
private const val COLOUR_PARTS = 6
private const val PERCENT_MAX = 100
private val COLOUR = Regex("#[0-9A-Fa-f]{6}(/\\d{1,3})?")
private val SHADOW_COLOUR = Regex("#[0-9A-Fa-f]{6}")

/**
 * One row of the docs/08 shaped-theme table. [colors] = six `#RRGGBB` or `#RRGGBB/NN` values (NN = opacity percent)
 * in the order bg, panel, text, muted, accent, border; [shape] = `"<border> <radius> <shadow>"` with the shadow
 * `none` or `y/blur/spread/#RRGGBB/NN`.
 *
 * @throws IllegalArgumentException when [colors] or [shape] is malformed.
 */
internal data class ShapedRow(
    val key: String,
    val name: String,
    val mode: ThemeMode,
    val colors: String,
    val shape: String,
    val typography: String,
) {
    /** The six listed colours by column. */
    val palette: Map<Column, Rgba> =
        colors
            .split(' ')
            .map { parseColour(it, key) }
            .also {
                require(it.size == COLOUR_PARTS) { "row $key needs $COLOUR_PARTS colours" }
            }.let { Column.entries.zip(it).toMap() }

    /** Border width, radius, shadow and backdrop of the row. */
    val shapeTokens: ShapeTokens = parseShape(shape, key, palette.getValue(Column.PANEL).isOpaque)

    /** Columns of the docs/08 table that hold a colour. */
    internal enum class Column { BG, PANEL, TEXT, MUTED, ACCENT, BORDER }
}

private fun parseColour(
    text: String,
    key: String,
): Rgba {
    require(COLOUR.matches(text)) { "row $key has a bad colour" }
    val opacity = text.substringAfter('/', "").toIntOrNull()
    val base = requireNotNull(Rgba.parse(text.substringBefore('/'))) { "row $key has a bad colour" }
    require(opacity == null || opacity in 0..PERCENT_MAX) { "row $key has a bad opacity" }
    return if (opacity == null) base else base.withAlpha(opacity)
}

private fun parseShape(
    shape: String,
    key: String,
    opaquePanel: Boolean,
): ShapeTokens {
    val parts = shape.split(' ')
    require(parts.size == SHAPE_PARTS) { "row $key shape needs border, radius and shadow" }
    val numbers = parts.take(2).map { requireNotNull(it.toIntOrNull()) { "row $key has a bad shape number" } }
    return ShapeTokens(
        radius = numbers[1],
        borderWidth = numbers[0],
        shadow = parseShadow(parts[2], key),
        backdrop = if (opaquePanel) Backdrop.OPAQUE else Backdrop.TRANSLUCENT,
    )
}

private fun parseShadow(
    text: String,
    key: String,
): ShadowSpec? {
    if (text == "none") return null
    val parts = text.split('/')
    require(
        parts.size == SHADOW_PARTS && SHADOW_COLOUR.matches(parts[SHADOW_COLOUR_AT]),
    ) { "row $key has a bad shadow" }
    val numbers =
        listOf(parts[0], parts[1], parts[2]).map {
            requireNotNull(it.toIntOrNull()) { "row $key has a bad shadow" }
        }
    val color = parseColour(parts[3] + "/" + parts[4], key)
    return ShadowSpec(offsetY = numbers[0], blur = numbers[1], spread = numbers[2], color = color)
}

/**
 * Fills the tokens docs/08 does not list (AM-805 plan): overlays come from the panel composited on bg, `muted` and
 * `work` are corrected by [withContrast], `warn`/`ok`/`err` by [deriveStatus].
 *
 * @throws IllegalStateException when [deriveStatus] finds no status triple for the accent.
 * @throws IllegalArgumentException when the bg column has an alpha below opaque (from `panel.over(bg)`).
 */
internal fun deriveShapedTokens(row: ShapedRow): ColorTokens {
    val p = row.palette
    val dark = row.mode.isDark
    val bg = p.getValue(ShapedRow.Column.BG)
    val panel = p.getValue(ShapedRow.Column.PANEL)
    val solid = panel.over(bg)
    val toward = if (dark) Rgba.WHITE else Rgba.BLACK
    val muted = p.getValue(ShapedRow.Column.MUTED).withContrast(listOf(bg, solid), MUTED_MIN, toward)
    val accent = p.getValue(ShapedRow.Column.ACCENT)
    val work = accent.withContrast(listOf(bg, solid), WORK_MIN, toward)
    val text = p.getValue(ShapedRow.Column.TEXT)
    return ColorTokens(
        surfaces = shapedSurfaces(dark, p, solid),
        content = ContentColors(text, muted, muted.mix(bg, DIM_PERCENT), accent, Wcag.inkFor(accent)),
        status = shapedStatus(deriveStatus(dark, accent), solid, work),
        terminal = if (dark) TerminalColors(bg, text) else TerminalColors(LIGHT_TERM_BG, LIGHT_TERM_TEXT),
    )
}

private fun shapedSurfaces(
    dark: Boolean,
    palette: Map<ShapedRow.Column, Rgba>,
    solid: Rgba,
): SurfaceColors {
    val text = palette.getValue(ShapedRow.Column.TEXT)
    val border = palette.getValue(ShapedRow.Column.BORDER)
    return SurfaceColors(
        bg = palette.getValue(ShapedRow.Column.BG),
        panel = palette.getValue(ShapedRow.Column.PANEL),
        raised = solid.mix(text, if (dark) RAISED_DARK else RAISED_LIGHT),
        sel = solid.mix(palette.getValue(ShapedRow.Column.ACCENT), if (dark) SEL_DARK else SEL_LIGHT),
        border = border,
        border2 = border.mix(text, BORDER2_PERCENT),
    )
}

private fun shapedStatus(
    triple: StatusTriple,
    solid: Rgba,
    work: Rgba,
): StatusColors =
    StatusColors(
        warn = triple.warn,
        warnInk = Wcag.inkFor(triple.warn),
        warnBg = solid.mix(triple.warn, WARN_BG_PERCENT),
        ok = triple.ok,
        err = triple.err,
        work = work,
    )
