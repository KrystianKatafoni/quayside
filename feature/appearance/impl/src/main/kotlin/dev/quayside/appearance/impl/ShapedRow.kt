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
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.mix
import dev.quayside.core.designtokens.over

private const val MARK_MIN = Wcag.NON_TEXT_MIN
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
    val solid = p.getValue(ShapedRow.Column.PANEL).over(bg)
    val text = p.getValue(ShapedRow.Column.TEXT)
    val given = p.getValue(ShapedRow.Column.ACCENT)
    val accent = markAccent(given, listOf(bg, solid, raisedOf(solid, text, dark)), solid, dark)
    val surfaces = shapedSurfaces(dark, p, solid, accent)
    val on = listOf(bg, solid, surfaces.sel)
    val status = shapedStatus(deriveStatus(dark, given), solid, accent.readableOn(on, dark), on, dark)
    val muted = p.getValue(ShapedRow.Column.MUTED).readableOn(on + status.warnBg, dark)
    return ColorTokens(
        surfaces = surfaces,
        content = ContentColors(text, muted, muted.mix(bg, DIM_PERCENT), accent, Wcag.inkFor(accent)),
        status = status,
        terminal = deriveTerminal(bg, text, dark),
    )
}

/**
 * The accent is a mark on bg, panel, raised and sel (3:1), corrected in one pass; sel is taken from the accent as
 * given. The final sel (mixed from the corrected accent) is checked for the whole catalog by `ScreenContrastTest`.
 * Unchanged when the accent meets the contrast already.
 */
private fun markAccent(
    given: Rgba,
    surfaces: List<Rgba>,
    solid: Rgba,
    dark: Boolean,
): Rgba {
    val sel = solid.mix(given, if (dark) SEL_DARK else SEL_LIGHT)
    return given.withContrast(surfaces + sel, MARK_MIN, toward(dark))
}

private fun toward(dark: Boolean): Rgba = if (dark) Rgba.WHITE else Rgba.BLACK

private fun raisedOf(
    solid: Rgba,
    text: Rgba,
    dark: Boolean,
): Rgba = solid.mix(text, if (dark) RAISED_DARK else RAISED_LIGHT)

private fun shapedSurfaces(
    dark: Boolean,
    palette: Map<ShapedRow.Column, Rgba>,
    solid: Rgba,
    accent: Rgba,
): SurfaceColors {
    val text = palette.getValue(ShapedRow.Column.TEXT)
    val border = palette.getValue(ShapedRow.Column.BORDER)
    return SurfaceColors(
        bg = palette.getValue(ShapedRow.Column.BG),
        panel = palette.getValue(ShapedRow.Column.PANEL),
        raised = raisedOf(solid, text, dark),
        sel = solid.mix(accent, if (dark) SEL_DARK else SEL_LIGHT),
        border = border,
        border2 = border.mix(text, BORDER2_PERCENT),
    )
}

private fun shapedStatus(
    triple: StatusTriple,
    solid: Rgba,
    work: Rgba,
    on: List<Rgba>,
    dark: Boolean,
): StatusColors {
    val warn = triple.warn.readableOn(on, dark)
    return StatusColors(
        warn = warn,
        warnInk = Wcag.inkFor(warn),
        warnBg = solid.mix(warn, WARN_BG_PERCENT),
        ok = triple.ok.readableOn(on, dark),
        err = triple.err.readableOn(on, dark),
        work = work,
    )
}
