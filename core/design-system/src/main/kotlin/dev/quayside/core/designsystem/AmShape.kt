package dev.quayside.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quayside.core.designtokens.Backdrop
import dev.quayside.core.designtokens.ShadowSpec
import dev.quayside.core.designtokens.ShapeTokens

/**
 * Shape tokens as Compose values. [corner] is a rounded corner of the token radius; [shadow] is drawn by [AmPanel]
 * behind its content and does not cut out the interior (it can show through a translucent panel);
 * [backdrop] is passed on as in [ShapeTokens], and TRANSLUCENT is the panel colour alpha over
 * the parent (no blur). [radii] are derived from the token radius ([row], [card], [tile] are their shapes);
 * [counter] is a full circle, or square when the token radius is 0.
 */
@Immutable
public data class AmShape(
    val corner: Shape,
    val borderWidth: Dp,
    val shadow: ShadowSpec?,
    val backdrop: Backdrop,
    val radii: CornerRadii,
    val counter: Shape,
) {
    /** Shape of a list row (radius r+2). */
    val row: Shape get() = RoundedCornerShape(radii.row)

    /** Shape of a card or panel (radius r+4). */
    val card: Shape get() = RoundedCornerShape(radii.card)

    /** Shape of an icon tile (radius r+2). */
    val tile: Shape get() = RoundedCornerShape(radii.tile)
}

/** Radii derived from the token radius r (docs/08): row r+2, card r+4, tile r+2; r = 0 gives 0 everywhere. */
@Immutable
public data class CornerRadii(
    val row: Dp,
    val card: Dp,
    val tile: Dp,
)

/** Radii for the token radius [radius]: row r+2, card r+4, tile r+2; a radius of 0 keeps every corner square. */
public fun cornerRadii(radius: Int): CornerRadii =
    if (radius == 0) {
        CornerRadii(0.dp, 0.dp, 0.dp)
    } else {
        CornerRadii((radius + ROW_EXTRA).dp, (radius + CARD_EXTRA).dp, (radius + TILE_EXTRA).dp)
    }

/** Maps shape tokens to [AmShape]; the counter is a full circle, or square when the radius is 0. */
public fun ShapeTokens.toAmShape(): AmShape =
    AmShape(
        corner = RoundedCornerShape(radius.dp),
        borderWidth = borderWidth.dp,
        shadow = shadow,
        backdrop = backdrop,
        radii = cornerRadii(radius),
        counter = if (radius == 0) RoundedCornerShape(0.dp) else RoundedCornerShape(percent = FULL_PERCENT),
    )

private const val ROW_EXTRA = 2
private const val CARD_EXTRA = 4
private const val TILE_EXTRA = 2
private const val FULL_PERCENT = 50
